package org.vaslabs.granger.repo

import java.time.format.DateTimeFormatter
import java.time.{Clock, ZonedDateTime}
import java.util.UUID

import akka.http.scaladsl.model.StatusCode
import cats.data.Kleisli
import org.vaslabs.granger.PatientManager
import org.vaslabs.granger.PatientManager.{LoadDataFailure, LoadDataSuccess}
import org.vaslabs.granger.comms.api.model.{Activity, AddToothInformationRequest}
import org.vaslabs.granger.modeltreatments._
import org.vaslabs.granger.modelv2._
import cats.effect._

/**
  * Created by vnicolaou on 03/06/17.
  */
class SingleStateGrangerRepo()(
  implicit repo: Repo[Map[PatientId, Patient]],
  clock: Clock

) extends GrangerRepo[Map[PatientId, Patient], IO] {

  private var state: Map[PatientId, Patient] = Map.empty

  override def addPatient(patient: Patient): IO[Patient] = {
    val patientId = PatientId(nextPatientId())
    val newState: Map[PatientId, Patient] = state + (patientId -> patient
      .update(patientId))
    val op = saveState.map(_ -> patientId) andThen unsafeGetPatientId
    op(s"Adding new patient with id ${patientId}", newState)
  }

  private[this] def saveState = Kleisli[IO, (String, Map[PatientId, Patient]), Map[PatientId, Patient]] {
    case (msg, newState) => IO {
      repo.save(msg, newState)
      state = newState
      newState
    }
  }

  private[this] def unsafeGetPatientId = Kleisli[IO, (Map[PatientId, Patient], PatientId), Patient] {
    case (state, patientId) => IO {state.get(patientId).get}
  }


  private[this] def nextPatientId(): Long = {
    if (state.size == 0)
      1L
    else
      state.keys.maxBy(_.id).id + 1L
  }

  override def retrieveAllPatients()
  : IO[Either[RepoErrorState, List[Patient]]] =
    IO {
      repo
        .getState()
        .map(
          s => {
            state = s
            state.values.toList
          }
        )
    }

  override def addToothInfo(rq: AddToothInformationRequest): Either[InvalidData, IO[Patient]] = {
    val newState = for {
      patient <- state.get(rq.patientId).toRight(PatientNotFound(rq.patientId))
      tooth <- patient.dentalChart.teeth.find(_.number == rq.toothNumber).toRight(ToothNotFound(rq.patientId, rq.toothNumber))
      toothWithNewInfo = tooth.update(
        rq.roots, rq.medicament,
        rq.nextVisit, rq.toothNote,
        rq.treatmentStarted, rq.obturation
      )
      newPatientState = patient.update(toothWithNewInfo)
    } yield (state + (patient.patientId -> newPatientState))

    updateStateAndRetrievePatient(
      s"New information for tooth ${rq.toothNumber} of patient ${rq.patientId}",
      newState,
      rq.patientId
    )
  }

  override def getLatestActivity(patientId: PatientId): Map[Int, List[Activity]] =
      state.get(patientId).map(_.extractLatestActivity).getOrElse(Map.empty)


  override def startTreatment(patientId: PatientId,
                              toothId: Int,
                              category: TreatmentCategory): Either[InvalidData, IO[Patient]] = {
    val treatment = Treatment(ZonedDateTime.now(clock), category = category)
    val newState = for {
      patient <- state.get(patientId).toRight(PatientNotFound(patientId))
      toothToStartTreatment <- patient.dentalChart.teeth.find(_.number == toothId).toRight(ToothNotFound(patientId, toothId))
      toothWithTreatment <- toothToStartTreatment.update(treatment).left.map(_ => ToothHasActiveTreatment(patientId, toothId))
      patientWithNewTreatment = patient.update(toothWithTreatment)
      stateWithTreatment = state + (patientId -> patientWithNewTreatment)
    } yield (stateWithTreatment)

    updateStateAndRetrievePatient(
      s"Started treatment for tooth $toothId on patient $patientId", newState, patientId
    )
  }

  def updateStateAndRetrievePatient(
      msg: String,
      newState: Either[InvalidData, Map[PatientId, Patient]],
      patientId: PatientId) = {
    newState.map(_ -> (saveState.map(_ -> patientId) andThen unsafeGetPatientId))
      .map {
        case (state, op) =>
          op(msg, state)
      }
  }

  override def finishTreatment(patientId: PatientId, toothId: Int, finishTime: ZonedDateTime): Either[InvalidData, IO[Patient]] = {

    val newState = for {
      patient <- state.get(patientId).toRight(PatientNotFound(patientId))
      toothWithTreatment <- patient.dentalChart.teeth.find(_.number == toothId).toRight(ToothNotFound(patientId, toothId))
      toothWithFinishedTreatment <- toothWithTreatment.finishTreatment(finishTime)
        .toRight(OpenTreatmentNotFound(patientId, toothId))
      patientWithFinishedTreatment = patient.update(toothWithFinishedTreatment)
      newState = state + (patientId -> patientWithFinishedTreatment)
    } yield (newState)

    updateStateAndRetrievePatient(
      s"Finished treatment for tooth $toothId on patient $patientId", newState, patientId
    )
  }

  override def loadData()
  : IO[PatientManager.LoadDataOutcome] =
    retrieveAllPatients().map(
      _.fold(res => LoadDataFailure(res), _ => LoadDataSuccess)
    )


  override def deleteTreatment(
        patientId: PatientId,
        toothId: Int,
        timestamp: ZonedDateTime): Either[InvalidData, IO[Patient]] = {

    val newState = for {
      patient <- state.get(patientId).toRight(PatientNotFound(patientId))
      updatedPatient = patient.deleteTreatment(toothId, timestamp)
      updatedState = state + (patientId -> updatedPatient)
    } yield (updatedState)

    val msg = s"Deleting treatment for patient $patientId, on tooth ${toothId}, created on ${timestamp.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)}"

    updateStateAndRetrievePatient(
      msg, newState, patientId
    )
  }

  override def setUpRepo(repoRq: Any): IO[StatusCode] = IO {
    repo.setUp()
  }

  override def pushChanges(): IO[Unit] = IO {
    repo.push()
  }

  import cats.syntax.either._

  override def deletePatient(patientId: PatientId): IO[Either[IOError, Unit]] = IO {
    val newState = state.get(patientId).map(_ => state.filterKeys(_ != patientId))
    Either.fromOption(newState, PatientNotFound(patientId))
      .flatMap(state => {
        repo.save(s"Deleted patient $patientId", state).map(_ => state)
      })
      .map(newState => state = newState)
  }

}

sealed trait IOError {
  def error: String
}

case class WriteError(error: String) extends IOError

case class CommitError(error: String) extends IOError

sealed trait InvalidData extends IOError

case class PatientNotFound(patientId: PatientId) extends InvalidData {
  val error: String = s"Patient $patientId does not exist"
}
case class ToothNotFound(patientId: PatientId, toothId: Int) extends InvalidData {
  val error: String = s"Patient $patientId does not have tooth $toothId"
}

case class ToothHasActiveTreatment(patientId: PatientId, toothId: Int) extends InvalidData {
  override def error: String = s"Patient ${patientId} has already an active treatment for tooth $toothId"
}

case class OpenTreatmentNotFound(patientId: PatientId, toothId: Int) extends InvalidData {
  override def error: String = s"Patient $patientId does not have an open treatment on tooth $toothId"
}
