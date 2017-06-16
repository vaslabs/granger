package org.vaslabs.granger.repo

import org.eclipse.jgit.api.Git
import org.vaslabs.granger.model
import org.vaslabs.granger.model.{Activity, Patient, PatientId, Root}

import scala.concurrent.{ExecutionContext, Future}
import org.vaslabs.granger.model.json._
import io.circe.syntax._
import java.io._

import cats.syntax.either._
import io.circe._
import org.vaslabs.granger.comms.api.model
import org.vaslabs.granger.comms.api.model.AddToothInformationRequest

import scala.io.Source

/**
  * Created by vnicolaou on 03/06/17.
  */
class GitBasedGrangerRepo(dbLocation: File)(implicit executionContext: ExecutionContext, gitApi: Git)
                                                              extends GrangerRepo[Future]{

  import git._
  import io.circe.generic.auto._
  import GitBasedGrangerRepo._

  private var repo: Map[PatientId, Patient] = Map.empty
  val snapshotFile = "patients.json"


  override def addPatient(patient: Patient): Future[Patient] = {
    Future {
      val patientId = PatientId(nextPatientId())
      val newState: Map[PatientId, Patient] = repo + (patientId -> patient.update(patientId))
      val payload = newState.asJson.noSpaces
      saveTo(snapshotFile, dbLocation, payload, s"Adding new patient with id ${patientId}")
      repo = newState
      repo.get(patientId).get
    }
  }

  private[this] def nextPatientId(): Long = {
    if (repo.size == 0)
      1L
    else
      repo.keys.maxBy(_.id).id + 1L
  }

  override def retrieveAllPatients(): Future[List[Patient]] = {
    Future {
      git.getFile(snapshotFile, dbLocation).map(
        file => {
          val json = Source.fromFile(file).getLines().mkString
          val newState: Option[Map[PatientId, Patient]] = parser.parse(json).flatMap(
            _.as[Map[PatientId, Patient]]
          ).toOption
          newState.foreach(repo = _)
        }
      )
      repo.values.toList
    }
  }

  override def addToothInfo(rq: AddToothInformationRequest): Future[Patient] =
    Future {
      val patient = repo.get(rq.patientId)
      patient.foreach(
        patient => {
        patient.dentalChart.teeth.find(_.number == rq.toothNumber).map(
          tooth => tooth.update(rq.roots, rq.medicament, rq.nextVisit, rq.toothNote)
      ).map(tooth => patient.update(tooth))
      .map(p => repo + (patient.patientId -> p))
      .foreach(newState => {
        saveTo(snapshotFile, dbLocation, newState.asJson.noSpaces, s"New information for tooth ${rq.toothNumber} of patient ${patient.patientId}")
        repo = newState
      }
      )
    })
    repo.get(rq.patientId).get
  }


  override def getLatestActivity(patientId: PatientId): Future[List[Activity]] = {
    Future {
      val patient = repo.get(patientId)
      patient.map(
        _.extractLatestActivity
      ).getOrElse(List.empty)
    }
  }
}

object GitBasedGrangerRepo {

  implicit val keyEncoder: KeyEncoder[PatientId] = KeyEncoder[Long].contramap(_.id)
  implicit val keyDecoder: KeyDecoder[PatientId] = KeyDecoder[Long].map(PatientId(_))

}

sealed trait IOError
case class WriteError(error: String) extends IOError

case class CommitError(error: String) extends IOError
