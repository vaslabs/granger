package org.vaslabs.granger.repo

import org.eclipse.jgit.api.Git
import org.vaslabs.granger.model
import org.vaslabs.granger.model.{Patient, PatientId}

import scala.concurrent.{ExecutionContext, Future}
import org.vaslabs.granger.model.json._
import io.circe.syntax._
import java.io._

import cats.syntax.either._
import io.circe._
/**
  * Created by vnicolaou on 03/06/17.
  */
class GitBasedGrangerRepo(implicit executionContext: ExecutionContext, gitApi: Git)
                                                              extends GrangerRepo[Future]{

  import git._
  import io.circe.generic.auto._
  import GitBasedGrangerRepo._

  private var repo: Map[PatientId, Patient] = Map.empty
  val snapshotFile = "patients.json"


  override def addPatient(patient: model.Patient): Future[model.Patient] = {
    Future {
      val patientId = PatientId(repo.keys.maxBy(_.id).id + 1)
      val newState: Map[PatientId, Patient] = repo + (patientId -> patient.copy(patientId = patientId))
      val payload = newState.asJson.noSpaces
      saveTo(snapshotFile, payload, s"Adding new patient with id ${patient.patientId}")
      repo = newState
      patient
    }
  }

  override def retrieveAllPatients(): Future[List[model.Patient]] = Future.successful(repo.values.toList)

  override def addToothDetails(patientId: model.PatientId, tooth: model.Tooth): Future[model.Patient] = {
    Future {
      repo.get(patientId).map(
        patient => {
          val patientTeeth = tooth :: patient.dentalChart.teeth.filter(_.number != tooth.number)
          patient.copy(dentalChart = patient.dentalChart.copy(patientTeeth))
        }
      ).map(
        patient => repo + (patient.patientId -> patient)
      ).map(newState =>
      {
        val message = s"Adding info for tooth ${tooth.number} of patient ${patientId.id}"
        saveTo(snapshotFile, newState.asJson.noSpaces, message).foreach( _ =>
          repo = newState
        )
      })
      repo.get(patientId).get
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
