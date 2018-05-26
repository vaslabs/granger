package org.vaslabs.granger.repo

import java.time.format.DateTimeFormatter
import java.time.{Clock, ZonedDateTime}

import akka.http.scaladsl.model.StatusCode
import org.vaslabs.granger.PatientManager
import org.vaslabs.granger.PatientManager.{LoadDataFailure, LoadDataSuccess}
import org.vaslabs.granger.comms.api.model.{Activity, AddToothInformationRequest}
import org.vaslabs.granger.modeltreatments._
import org.vaslabs.granger.modelv2._

import scala.concurrent.ExecutionContext
import cats.effect._
/**
  * Created by vnicolaou on 03/06/17.
  */
class SingleStateGrangerRepo()(implicit val executionContext: ExecutionContext,
                               clock: Clock)
    extends GrangerRepo[Map[PatientId, Patient], IO] {

  private var state: Map[PatientId, Patient] = Map.empty

  override def addPatient(patient: Patient)(
      implicit repo: Repo[Map[PatientId, Patient]]): IO[Patient] = IO
    {
      val patientId = PatientId(nextPatientId())
      val newState: Map[PatientId, Patient] = state + (patientId -> patient
        .update(patientId))
      repo.save(s"Adding new patient with id ${patientId}", newState)
      state = newState
      state.get(patientId).get
    }


  private[this] def nextPatientId(): Long = {
    if (state.size == 0)
      1L
    else
      state.keys.maxBy(_.id).id + 1L
  }

  override def retrieveAllPatients()(
      implicit repo: Repo[Map[PatientId, Patient]])
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

  override def addToothInfo(rq: AddToothInformationRequest)(
      implicit repo: Repo[Map[PatientId, Patient]]): IO[Patient] = IO
    {
      val patient = state.get(rq.patientId)
      patient.foreach(patient => {
        patient.dentalChart.teeth
          .find(_.number == rq.toothNumber)
          .map(
            tooth =>
              tooth.update(
                rq.roots, rq.medicament,
                rq.nextVisit, rq.toothNote,
                rq.treatmentStarted, rq.obturation
              )
          )
          .map(tooth => patient.update(tooth))
          .map(p => state + (patient.patientId -> p))
          .foreach(newState => {
            repo.save(
              s"New information for tooth ${rq.toothNumber} of patient ${patient.patientId}",
              newState)
            state = newState
          })
      })
      state.get(rq.patientId).get
    }

  override def getLatestActivity(
      patientId: PatientId): IO[Map[Int, List[Activity]]] =
    IO {
      val patient = state.get(patientId)
      patient.map(_.extractLatestActivity).getOrElse(Map.empty)
    }

  override def startTreatment(patientId: PatientId,
                              toothId: Int,
                              category: TreatmentCategory)(
      implicit repo: Repo[Map[PatientId, Patient]]): IO[Patient] = IO {
    val treatment = Treatment(ZonedDateTime.now(clock), category = category)
    val patient = state.get(patientId).get
    val toothWithTreatment =
      patient.dentalChart.teeth
        .find(_.number == toothId)
        .flatMap(_.update(treatment).toOption)
    toothWithTreatment
      .map(t => patient.update(t))
      .map(p => state + (patientId -> p))
      .foreach(newRepo => {
        repo
          .save(s"Started treatment for tooth $toothId on patient $patientId",
                newRepo)
          .foreach(_ => state = newRepo)
      })
    state.get(patientId).get
  }

  override def finishTreatment(patientId: PatientId, toothId: Int)(
      implicit repo: Repo[Map[PatientId, Patient]]): IO[Patient] = IO{
    val newRepo = state
      .get(patientId)
      .flatMap(p => {
        p.dentalChart.teeth
          .find(_.number == toothId)
          .flatMap(
            t => t.finishTreatment()
          )
          .map(p.update(_))
      })
      .map(p => state + (patientId -> p))
    newRepo.foreach { newRepo =>
      repo
        .save(s"Finished treatment for tooth $toothId on patient $patientId",
              newRepo)
        .foreach(_ => state = newRepo)
    }
    state.get(patientId).get
  }

  override def loadData()(implicit repo: Repo[Map[PatientId, Patient]])
    : IO[PatientManager.LoadDataOutcome] =
    retrieveAllPatients().map(
      _.fold(res => LoadDataFailure(res), _ => LoadDataSuccess)
    )


  override def deleteTreatment(
      patientId: PatientId,
      toothId: Int,
      timestamp: ZonedDateTime)(implicit repo: Repo[Map[PatientId, Patient]]): IO[Patient] = IO
    {
      val updatedPatient = state.get(patientId).map(_.deleteTreatment(toothId, timestamp))
      updatedPatient.foreach(p => {
        state = state + (patientId -> p)
        repo.save(
          s"Deleting treatment for patient $patientId, on tooth ${toothId}, created on ${timestamp.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)}",
          state)
      })
      state.get(patientId).get
    }

  override def setUpRepo(repoRq: Any)(implicit repo: Repo[Map[PatientId, Patient]]): IO[StatusCode] = IO {repo.setUp()}

  override def pushChanges()(implicit repo: Repo[Map[PatientId, Patient]]): IO[Unit] = IO {repo.push()}

  import cats.syntax.either._

  override def deletePatient(patientId: PatientId)(implicit repo: Repo[Map[PatientId, Patient]]): IO[Either[IOError, Unit]] = IO {
    val newState = state.get(patientId).map(_ => state.filterKeys(_ != patientId))
    Either.fromOption(newState, PatientNotFound(patientId))
      .flatMap(state =>
        {
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

case class PatientNotFound(patientId: PatientId) extends IOError {
  val error: String = s"Patient $patientId does not exist"
}