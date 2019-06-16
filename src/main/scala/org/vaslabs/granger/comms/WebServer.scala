package org.vaslabs.granger.comms

import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.Executors

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.stream.typed.scaladsl.ActorMaterializer
import akka.util.Timeout
import akka.actor.typed.scaladsl.AskPattern._
import org.vaslabs.granger.comms.api.model._
import org.vaslabs.granger.modelv2._
import org.vaslabs.granger.repo.{IOError, InvalidData}
import org.vaslabs.granger._
import org.vaslabs.granger.reminders.{AllPatientReminders, DeletedAck, Notify, SnoozeAck}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

/**
 * Created by vnicolaou on 28/05/17.
 */
class WebServer(
    patientManager: ActorRef[Protocol],
    rememberInputAgent: ActorRef[RememberInputAgent.Protocol],
    config: GrangerConfig)(implicit actorSystem: ActorSystem[_])
    extends GrangerApi[Future]
    with HttpRouter {

  private val fastCalcExecutionContext = ExecutionContext.global
  private val ioExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))
  implicit val timeout = Timeout(5 seconds)
  private implicit val untypedActorSystem = actorSystem.toUntyped
  private implicit val scheduler = actorSystem.scheduler

  def start(): Unit = {

    implicit val httpActorMaterializer = ActorMaterializer()
    Http().bindAndHandle(routes, config.bindAddress, config.bindPort)
  }

  def shutDown(): Future[Unit] = {
    implicit val ec = fastCalcExecutionContext
    Http().shutdownAllConnectionPools().andThen {
      case _ => actorSystem.terminate()
    }
  }

  override def addPatient(patient: Patient): Future[Patient] =
    patientManager.ask[Patient](replyTo => AddPatient(patient, replyTo))

  override def retrieveAllPatients(): Future[List[Patient]] =
    patientManager ? FetchAllPatients

  private type ActorResponse = Either[InvalidData, Patient]
  private def toFailure(invalidData: InvalidData): Failure =
    Failure(invalidData.error)

  override def addToothInfo(rq: AddToothInformationRequest): Future[Either[Failure, Patient]] = {
    patientManager
      .ask[ActorResponse](replyTo => AddToothInformation(rq, replyTo))
      .map(_.left.map(toFailure))(fastCalcExecutionContext)
  }

  def getLatestActivity(patientId: PatientId): Future[Map[Int, List[Activity]]] =
    patientManager ? (replyTo => LatestActivity(patientId, replyTo))

  def getPublicKey(): Future[PubKey] =
    Future {
      val keyValue =
        Source.fromFile(s"${config.keysLocation}/id_rsa.pub").mkString
      PubKey(keyValue)
    }(ioExecutionContext)

  override def initGitRepo(remoteRepo: RemoteRepo): Future[StatusCode] =
    Future.successful(StatusCodes.Forbidden)

  override def startNewTreatment(startTreatment: UserApi.StartTreatment): Future[Response] = {
    def actorMessage(actorRef: ActorRef[ActorResponse]) =
      StartTreatment(startTreatment.patientId, startTreatment.toothId, startTreatment.category, actorRef)
    patientManager ? actorMessage
  }.map(_.left.map(toFailure))(fastCalcExecutionContext)

  override def finishTreatment(finishTreatment: UserApi.FinishTreatment): Future[Response] = {
    def actorMessage(actorRef: ActorRef[ActorResponse]) =
      FinishTreatment(finishTreatment.patientId, finishTreatment.toothId, finishTreatment.finishedOn, actorRef)
    patientManager ? actorMessage
  }.map(_.left.map(toFailure))(fastCalcExecutionContext)

  override def rememberedData(): Future[AutocompleteSuggestions] = {
    rememberInputAgent ? RememberInputAgent.Suggest
  }

  override def deleteTreatment(deleteTreatment: UserApi.DeleteTreatment): Future[Response] = {
    def actorMessage(actorRef: ActorRef[ActorResponse]) = {
      DeleteTreatment(deleteTreatment.patientId, deleteTreatment.toothId, deleteTreatment.createdOn, actorRef)
    }
    patientManager ? actorMessage
  }.map(_.left.map(toFailure))(fastCalcExecutionContext)

  override def deletePatient(patientId: PatientId): Future[CommandOutcome] =
    patientManager ? (replyTo => DeletePatient(patientId, replyTo))

  override def treatmentNotifications(timestamp: ZonedDateTime): Future[Notify] =
    patientManager ? (replyTo => GetTreatmentNotifications(timestamp, replyTo))

  override def modifyReminder(rq: UserApi.ModifyReminder): Future[SnoozeAck] = {
    def actorMessage(actorRef: ActorRef[SnoozeAck]) =
      ModifyReminderRQ(rq.reminderTimestamp, rq.snoozeTo, rq.patientId, actorRef)
    patientManager ? actorMessage
  }

  override def deleteReminder(patientId: PatientId, timestamp: ZonedDateTime): Future[DeletedAck] =
    patientManager ? (replyTo => StopReminder(timestamp, patientId, replyTo))

  override def allReminders(patientId: PatientId): Future[AllPatientReminders] =
    patientManager ? (replyTo => FetchAllPatientReminders(patientId, replyTo))

  override def storeImage(patientId: PatientId): Future[UUID] =
    patientManager ? (replyTo => StoreImageRQ(patientId, Array(0), replyTo))

  override def getImages(patientId: PatientId): Future[PatientImages] =
    patientManager ? (replyTo => GetPatientImages(patientId, replyTo))
}
