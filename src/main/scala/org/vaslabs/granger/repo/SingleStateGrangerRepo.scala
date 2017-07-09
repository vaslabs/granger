package org.vaslabs.granger.repo

import java.io._
import java.time.{Clock, ZonedDateTime}

import org.vaslabs.granger.PatientManager
import org.vaslabs.granger.PatientManager.{LoadDataFailure, LoadDataSuccess}
import org.vaslabs.granger.comms.api.model.{Activity, AddToothInformationRequest}
import org.vaslabs.granger.modeltreatments._
import org.vaslabs.granger.modelv2._

import scala.concurrent.{ExecutionContext, Future}


/**
  * Created by vnicolaou on 03/06/17.
  */
class SingleStateGrangerRepo()(implicit val executionContext: ExecutionContext, clock: Clock)
                                                              extends GrangerRepo[Map[PatientId, Patient], Future]{

  private var state: Map[PatientId, Patient] = Map.empty

  override def addPatient(patient: Patient)(implicit repo: Repo[Map[PatientId, Patient]]): Future[Patient] = {
    Future {
      val patientId = PatientId(nextPatientId())
      val newState: Map[PatientId, Patient] = state + (patientId -> patient.update(patientId))
      repo.save(s"Adding new patient with id ${patientId}", newState)
      state = newState
      state.get(patientId).get
    }
  }

  private[this] def nextPatientId(): Long = {
    if (state.size == 0)
      1L
    else
      state.keys.maxBy(_.id).id + 1L
  }

  override def retrieveAllPatients()(implicit repo: Repo[Map[PatientId, Patient]]): Future[Either[NotReady, List[Patient]]] =
    Future {
      repo.getState().map(
          s => {
          state = s
          state.values.toList
        }
      )
    }

  override def addToothInfo(rq: AddToothInformationRequest)(implicit repo: Repo[Map[PatientId, Patient]]): Future[Patient] =
    Future {
      val patient = state.get(rq.patientId)
      patient.foreach(
        patient => {
        patient.dentalChart.teeth.find(_.number == rq.toothNumber).map(
          tooth => tooth.update(rq.roots, rq.medicament, rq.nextVisit, rq.toothNote)
      ).map(tooth => patient.update(tooth))
      .map(p => state + (patient.patientId -> p))
      .foreach(newState => {
        repo.save(s"New information for tooth ${rq.toothNumber} of patient ${patient.patientId}", newState)
        state = newState
      }
      )
    })
    state.get(rq.patientId).get
  }


  override def getLatestActivity(patientId: PatientId): Future[Map[Int, List[Activity]]] =
    Future {
      val patient = state.get(patientId)
      patient.map(_.extractLatestActivity
      ).getOrElse(Map.empty)
    }

  override def startTreatment(patientId: PatientId, toothId: Int, category: TreatmentCategory)(implicit repo: Repo[Map[PatientId, Patient]]): Future[Patient] = Future {
    val treatment = Treatment(ZonedDateTime.now(clock), category = category)
    val patient = state.get(patientId).get
    val toothWithTreatment =
      patient.dentalChart.teeth.find(_.number == toothId)
                               .flatMap(_.update(treatment).toOption)
    toothWithTreatment.map( t =>
      patient.update(t)
    ).map( p => state + (patientId -> p))
      .foreach(newRepo => {
        repo.save(s"Started treatment for tooth $toothId on patient $patientId", newRepo)
        .foreach(_ => state = newRepo)
      })
    state.get(patientId).get
  }

  override def finishTreatment(patientId: PatientId, toothId: Int)(implicit repo: Repo[Map[PatientId, Patient]]): Future[Patient] = Future {
    val newRepo = state.get(patientId)
        .flatMap( p => {
          p.dentalChart.teeth.find(_.number == toothId).flatMap(
            t => t.finishTreatment()
          ).map(p.update(_))
        }).map(p => state + (patientId -> p))
    newRepo.foreach { newRepo =>
      repo.save(s"Finished treatment for tooth $toothId on patient $patientId", newRepo)
        .foreach(_ => state = newRepo)
    }
    state.get(patientId).get
  }

  override def loadData()(implicit repo: Repo[Map[PatientId, Patient]]): Future[PatientManager.LoadDataOutcome] = {
    retrieveAllPatients().map(_.map(_ => LoadDataSuccess)
          .getOrElse(LoadDataFailure))
  }
}

sealed trait IOError
case class WriteError(error: String) extends IOError

case class CommitError(error: String) extends IOError
