package org.vaslabs.granger.repo

import org.eclipse.jgit.api.Git
import org.vaslabs.granger.model.{Patient, PatientId, Tooth, Treatment}

import scala.concurrent.{ExecutionContext, Future}
import org.vaslabs.granger.model.json._
import io.circe.syntax._
import java.io._
import java.time.{Clock, ZonedDateTime}

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import cats.syntax.either._
import io.circe._
import org.eclipse.jgit.transport.URIish
import org.vaslabs.granger.comms.api.model
import org.vaslabs.granger.comms.api.model.{Activity, AddToothInformationRequest, GitRepo}
import org.vaslabs.granger.model

import scala.io.Source
import scala.util.Try

/**
  * Created by vnicolaou on 03/06/17.
  */
class GitBasedGrangerRepo(dbLocation: File)(implicit executionContext: ExecutionContext, gitApi: Git, clock: Clock)
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

  override def retrieveAllPatients(): Future[Either[NotReady, List[Patient]]] = {
    Future {
      git.getFile(snapshotFile, dbLocation).map(
        file => {
          val json = Source.fromFile(file).getLines().mkString
          val newState: Option[Map[PatientId, Patient]] = parser.parse(json).flatMap(
            _.as[Map[PatientId, Patient]]
          ).toOption
          newState.foreach(repo = _)
          repo.values.toList
        }
      ).left.map(e => NotReady(e.error))
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


  override def getLatestActivity(patientId: PatientId): Future[Map[Int, List[Activity]]] = {
    Future {
      val patient = repo.get(patientId)
      patient.map(_.extractLatestActivity
      ).getOrElse(Map.empty)
    }
  }

  override def setUpRepo(gitRepo: GitRepo): Future[StatusCode] = {
    Future {
      Try {
        val remoteAddCommand = gitApi.remoteAdd()
        remoteAddCommand.setName("origin")
        remoteAddCommand.setUri(new URIish(gitRepo.uri))
        remoteAddCommand.call()
        val file = new File(s"${dbLocation.getAbsolutePath}/$snapshotFile")
        file.createNewFile()
        saveTo(snapshotFile, dbLocation, "[]", "Empty db file")
      }.map(_ => StatusCodes.Created)
      .getOrElse(StatusCodes.InternalServerError)
    }
  }

  override def pushChanges(): Unit = {
    Future {
      gitApi.push().call()
      ()
    }
    ()
  }

  override def startTreatment(patientId: PatientId, toothId: Int, info: String): Future[Patient] = Future {
    val treatment = Treatment(ZonedDateTime.now(clock), info = info)
    val patient = repo.get(patientId).get
    val toothWithTreatment =
      patient.dentalChart.teeth.find(_.number == toothId)
                               .flatMap(_.update(treatment).toOption)
    toothWithTreatment.map( t =>
      patient.update(t)
    ).map( p => repo + (patientId -> p))
      .foreach(newRepo => {
        saveTo(snapshotFile, dbLocation, newRepo.asJson.noSpaces, s"Started treatment for tooth $toothId on patient $patientId")
          .foreach(_ => repo = newRepo)
      })
    repo.get(patientId).get
  }

  override def finishTreatment(patientId: PatientId, toothId: Int): Future[Patient] = Future {
    val newRepo = repo.get(patientId)
        .flatMap( p => {
          p.dentalChart.teeth.find(_.number == toothId).flatMap(
            t => t.finishTreatment()
          ).map(p.update(_))
        }).map(p => repo + (patientId -> p))
    newRepo.foreach { newRepo =>
      saveTo(snapshotFile, dbLocation, newRepo.asJson.noSpaces, s"Finished treatment for tooth $toothId on patient $patientId")
        .foreach(_ => repo = newRepo)
    }
    repo.get(patientId).get
  }
}

object GitBasedGrangerRepo {

  implicit val keyEncoder: KeyEncoder[PatientId] = KeyEncoder[Long].contramap(_.id)
  implicit val keyDecoder: KeyDecoder[PatientId] = KeyDecoder[Long].map(PatientId(_))

}

sealed trait IOError
case class WriteError(error: String) extends IOError

case class CommitError(error: String) extends IOError
