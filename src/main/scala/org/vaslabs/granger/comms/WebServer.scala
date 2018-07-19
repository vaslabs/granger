package org.vaslabs.granger.comms

import java.time.ZonedDateTime

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCode
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContext, Future}
import akka.pattern._
import akka.util.Timeout
import org.vaslabs.granger.{GrangerConfig, PatientManager, RememberInputAgent}
import org.vaslabs.granger.PatientManager._
import org.vaslabs.granger.comms.api.model._
import org.vaslabs.granger.modelv2._
import org.vaslabs.granger.reminders.RCTReminderActor
import org.vaslabs.granger.reminders.RCTReminderActor.Protocol.External
import org.vaslabs.granger.reminders.RCTReminderActor.Protocol.External.SnoozeAck
import org.vaslabs.granger.repo.IOError

import scala.concurrent.duration._
import scala.io.Source

/**
  * Created by vnicolaou on 28/05/17.
  */
class WebServer(patientManager: ActorRef, rememberInputAgent: ActorRef, config: GrangerConfig)(
    implicit executionContext: ExecutionContext,
    actorSystem: ActorSystem,
    materializer: ActorMaterializer)
    extends GrangerApi[Future]
    with HttpRouter {

  implicit val timeout = Timeout(5 seconds)

  def start(): Unit = {
    Http().bindAndHandle(routes, config.bindAddress, config.bindPort)
  }

  def shutDown(): Future[Unit] = {
    Http().shutdownAllConnectionPools() andThen {
      case _ => actorSystem.terminate()
    }
  }

  override def addPatient(patient: Patient): Future[Patient] =
    (patientManager ? AddPatient(patient)).mapTo[Patient]

  override def retrieveAllPatients()
    : Future[List[Patient]] =
    (patientManager ? FetchAllPatients)
      .mapTo[List[Patient]]

  override def addToothInfo(rq: AddToothInformationRequest): Future[Patient] = {
    (patientManager ? rq).mapTo[Patient]
  }

  def getLatestActivity(
      patientId: PatientId): Future[Map[Int, List[Activity]]] =
    (patientManager ? LatestActivity(patientId))
      .mapTo[Map[Int, List[Activity]]]

  def getPublicKey(): Future[PubKey] =
    Future {
      val keyValue =
        Source.fromFile(s"${config.keysLocation}/id_rsa.pub").mkString
      PubKey(keyValue)
    }

  override def initGitRepo(remoteRepo: RemoteRepo): Future[StatusCode] =
    (patientManager ? InitRepo(remoteRepo)).mapTo[StatusCode]

  override def startNewTreatment(
      startTreatment: StartTreatment): Future[Either[Failure, Patient]] =
    (patientManager ? startTreatment).map {
      case p: Patient =>
        Right(p)
      case io: IOError => Left(Failure(io.error))
      case _ => Left(Failure("Unknown error"))
    }

  override def finishTreatment(
      finishTreatment: FinishTreatment): Future[Patient] =
    (patientManager ? finishTreatment).mapTo[Patient]

  override def rememberedData(): Future[AutocompleteSuggestions] = {
    (rememberInputAgent ? RememberInputAgent.Suggest).mapTo[AutocompleteSuggestions]
  }

  override def deleteTreatment(deleteTreatment: DeleteTreatment): Future[Patient] =
    (patientManager ? deleteTreatment).mapTo[Patient]

  override def deletePatient(patientId: PatientId): Future[CommandOutcome] =
    (patientManager ? DeletePatient(patientId)).mapTo[CommandOutcome]

  override def treatmentNotifications(timestamp: ZonedDateTime): Future[External.Notify] =
    (patientManager ? GetTreatmentNotifications(timestamp)).mapTo[External.Notify]

  override def modifyReminder(rq: External.ModifyReminder): Future[External.SnoozeAck] = {
    (patientManager ? rq).mapTo[SnoozeAck]
  }

  override def deleteReminder(patientId: PatientId, timestamp: ZonedDateTime): Future[External.DeletedAck] =
    (patientManager ? PatientManager.StopReminder(timestamp, patientId)).mapTo[External.DeletedAck]

  override def allReminders(patientId: PatientId): Future[External.AllPatientReminders] =
    (patientManager ? External.PatientReminders(patientId)).mapTo[External.AllPatientReminders]
}
