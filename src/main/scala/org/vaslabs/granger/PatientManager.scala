package org.vaslabs.granger

import java.io.File
import java.time.{Clock, ZonedDateTime}
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import cats.effect.IO
import org.eclipse.jgit.api.Git
import org.vaslabs.granger.comms.api.model.{AddToothInformationRequest, RemoteRepo}
import org.vaslabs.granger.modeltreatments.TreatmentCategory
import org.vaslabs.granger.modelv2.{Patient, PatientId}
import org.vaslabs.granger.reminders.RCTReminderActor
import org.vaslabs.granger.reminders.RCTReminderActor.Protocol.External.{ModifyReminder, SetReminder}
import org.vaslabs.granger.repo.git.{EmptyProvider, GitRepo}
import org.vaslabs.granger.repo._
/**
  * Created by vnicolaou on 29/05/17.
  */
class PatientManager private (
    grangerConfig: GrangerConfig)(implicit gitApi: Git, clock: Clock)
    extends Actor
    with ActorLogging {
  import PatientManager._
  import io.circe.generic.auto._
  import io.circe.generic.semiauto._
  import v2json._

  implicit val emptyPatientsProvider: EmptyProvider[Map[PatientId, Patient]] = () => Map.empty


  implicit val gitRepo: GitRepo[Map[PatientId, Patient]] =
    new GitRepo[Map[PatientId, Patient]](new File(grangerConfig.repoLocation), "patients.json")

  final val grangerRepo: GrangerRepo[Map[PatientId, Patient], IO] = new SingleStateGrangerRepo()

  final val gitRepoPusher: ActorRef =
    context.actorOf(GitRepoPusher.props(grangerRepo), "gitPusher")

  val notificationActor = context.actorOf(RCTReminderActor.props, "notifications")

  override def receive: Receive = {
    case LoadData =>
      log.info("Loading patient data...")
      self ! grangerRepo.loadData().unsafeRunSync()
    case LoadDataSuccess =>
      log.info("Done")
      context.become(receivePostLoad)
    case LoadDataFailure(repoState) =>
      repoState match {
        case EmptyRepo =>
          context.become(settingUp)
        case _ =>
          log.error("Failed to load repo: " + repoState)
          self ! PoisonPill
      }
  }

  private[this] def handleRetrievingPatientData(): Unit = {
    val senderRef = sender()
    grangerRepo.retrieveAllPatients().unsafeRunSync() match {
      case Left(errorState) => senderRef ! errorState
      case Right(patientData) => senderRef ! patientData
    }
  }

  override def aroundPreStart(): Unit = {
    context.system.eventStream.subscribe(notificationActor, classOf[SetReminder])
    super.aroundPreStart()
  }

  def settingUp: Receive = {
    case FetchAllPatients =>
      handleRetrievingPatientData()

    case InitRepo(remoteRepo) =>
      context.become(receivePostLoad)
      sender() ! grangerRepo.setUpRepo(remoteRepo)
  }


  def receivePostLoad: Receive = {
    getInformation orElse submitInformation
  }

  private [this] def getInformation: Receive = {
    case FetchAllPatients =>
      handleRetrievingPatientData()
    case LatestActivity(patientId) =>
      sender() ! grangerRepo.getLatestActivity(patientId)
  }

  private [this] def submitInformation: Receive = {
    case AddPatient(patient) =>
      schedulePushJob()
      sender() ! grangerRepo.addPatient(patient).unsafeRunSync()
    case rq: AddToothInformationRequest =>
      schedulePushJob()
      val senderRef = sender()
      grangerRepo.addToothInfo(rq).map(_.unsafeRunSync()).map(senderRef ! _).merge
      context.system.eventStream.publish(rq)
    case StartTreatment(patientId, toothId, category) =>
      schedulePushJob()
      sender() ! grangerRepo.startTreatment(patientId, toothId, category).map(_.unsafeRunSync()).merge
    case FinishTreatment(patientId, toothId) =>
      schedulePushJob()
      val finishTime = ZonedDateTime.now(clock)
      val outcome = grangerRepo.finishTreatment(patientId, toothId, finishTime).map(_.unsafeRunSync())
      outcome.foreach(_ => {
        context.system.eventStream.publish(SetReminder(finishTime, finishTime.plusMonths(6), patientId))
      })

      sender() ! outcome.merge
    case DeleteTreatment(patientId, toothId, timestamp) =>
      schedulePushJob()
      sender() !
        grangerRepo.deleteTreatment(patientId, toothId, timestamp).map(_.unsafeRunSync()).merge
    case DeletePatient(patientId) =>
      schedulePushJob()
      val outcome = grangerRepo.deletePatient(patientId)
        .map(_.map(_ => Success).left.map(_ => Failure(s"Failed to delete patient ${patientId}"))).unsafeRunSync()
      sender() ! outcome.merge

    case GetTreatmentNotifications(time) =>
      notificationActor forward RCTReminderActor.Protocol.External.CheckReminders(time)

    case mr: ModifyReminder => notificationActor forward mr
    case StopReminder(timestamp, patientId) =>
      notificationActor forward RCTReminderActor.Protocol.External.DeleteReminder(timestamp, patientId, ZonedDateTime.now(clock))

  }

  private[this] def schedulePushJob(): Unit = {
    gitRepoPusher ! GitRepoPusher.PushChanges
  }

}

object PatientManager {
  def props(grangerConfig: GrangerConfig)(implicit gitApi: Git,
                                          clock: Clock): Props =
    Props(new PatientManager(grangerConfig))

  case object FetchAllPatients

  case class AddPatient(patient: modelv2.Patient)

  case class LatestActivity(patientId: PatientId)

  case class InitRepo(remoteRepo: RemoteRepo)

  case class StartTreatment(patientId: PatientId,
                            toothId: Int,
                            category: TreatmentCategory)
  case class DeleteTreatment(patientId:PatientId, toothId: Int, createdOn: ZonedDateTime)
  case class FinishTreatment(patientId: PatientId, toothId: Int)

  case object LoadData
  sealed trait LoadDataOutcome
  case object LoadDataSuccess extends LoadDataOutcome
  case class LoadDataFailure(repoState: RepoErrorState) extends LoadDataOutcome

  case object RememberedData

  case class DeletePatient(patientId: PatientId)

  case class GetTreatmentNotifications(time: ZonedDateTime)

  sealed trait CommandOutcome
  case object Success extends CommandOutcome
  case class Failure(reason: String) extends CommandOutcome

  case class StopReminder(timestamp: ZonedDateTime, patientId: PatientId)

}
