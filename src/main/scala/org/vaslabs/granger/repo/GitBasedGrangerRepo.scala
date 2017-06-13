package org.vaslabs.granger.repo

import org.eclipse.jgit.api.Git
import org.vaslabs.granger.model
import org.vaslabs.granger.model.{Patient, PatientId, Root}

import scala.concurrent.{ExecutionContext, Future}
import org.vaslabs.granger.model.json._
import io.circe.syntax._
import java.io._

import cats.syntax.either._
import io.circe._
import org.vaslabs.granger.comms.api.model
import org.vaslabs.granger.comms.api.model.{RootRequest, ToothNoteRequest}

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
      val newState: Map[PatientId, Patient] = repo + (patientId -> patient.copy(patientId = patientId))
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

  override def addToothRoots(patientId: PatientId, rootRequest: RootRequest): Future[Patient] = {
    Future {
      repo.get(patientId).map(
        patient => {
          patient.update(rootRequest.toothNumber, rootRequest.root)
        }
      ).map(
        patient => repo + (patient.patientId -> patient)
      ).map(newState =>
      {
        val message = s"Adding root ${rootRequest.root} for tooth ${rootRequest.toothNumber} of patient ${patientId.id}"
        saveTo(snapshotFile, dbLocation, newState.asJson.noSpaces, message).foreach( _ =>
          repo = newState
        )
      })
      repo.get(patientId).get
    }
  }

  override def addToothNotes(patientId: PatientId, toothNoteRequest: ToothNoteRequest): Future[Patient] = {
    Future {
      repo.get(patientId).map(
        _.update(toothNoteRequest.toothNumber, toothNoteRequest.toothNote)
      ).map(
        p => repo + (patientId -> p)
      ).map(
        newState => {
          val message = s"Adding notes ${toothNoteRequest.toothNote} for tooth ${toothNoteRequest.toothNumber} of patient ${patientId}"
          saveTo(snapshotFile, dbLocation, newState.asJson.noSpaces, message).foreach(
            _ => repo = newState
          )
        }
      )
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
