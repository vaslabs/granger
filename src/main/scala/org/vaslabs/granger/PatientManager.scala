package org.vaslabs.granger

import scala.concurrent.duration._
import java.io.File
import java.time.{Clock, ZoneOffset, ZonedDateTime}

import akka.actor.typed.eventstream.Publish
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.actor.typed.scaladsl.adapter._
import cats.effect.IO
import org.eclipse.jgit.api.Git
import org.vaslabs.granger.RememberInputAgent.MedicamentSeen
import org.vaslabs.granger.comms.api.model.{Activity, AddToothInformationRequest, RemoteRepo}
import org.vaslabs.granger.modeltreatments.TreatmentCategory
import org.vaslabs.granger.modelv2.{Patient, PatientId}
import org.vaslabs.granger.reminders._
import org.vaslabs.granger.repo.git.{EmptyProvider, GitRepo}
import org.vaslabs.granger.repo._


object PatientManager {


  import io.circe.generic.auto._
  import v2json._
  implicit val emptyPatientsProvider: EmptyProvider[Map[PatientId, Patient]] = () => Map.empty

  private type GrangerRepoType = GrangerRepo[Map[PatientId, Patient], IO]

  def behavior(grangerConfig: GrangerConfig)(implicit gitApi: Git, clock: Clock): Behavior[Protocol] =
    Behaviors.supervise(unsafeSetupBehaviour(grangerConfig)).onFailure[RuntimeException](
        SupervisorStrategy
          .restartWithBackoff(minBackoff = 10 seconds, maxBackoff = 5 minutes, randomFactor = 0.2)
          .withMaxRestarts(10)
        )


  private def unsafeSetupBehaviour(grangerConfig: GrangerConfig)(implicit gitApi: Git, clock: Clock): Behavior[Protocol] =
    Behaviors.setup { ctx =>
      implicit val gitRepo: GitRepo[Map[PatientId, Patient]] =
        new GitRepo[Map[PatientId, Patient]](new File(grangerConfig.repoLocation), "patients.json")


      val grangerRepo: GrangerRepoType = new SingleStateGrangerRepo()
      val notificationActor = ctx.spawn(RCTReminderActor.behaviour(grangerConfig.repoLocation), "notifications")
      val noopActor = ctx.spawnAnonymous[Any](Behaviors.ignore)
      val gitRepoPusher: akka.actor.ActorRef =
        ctx.actorOf(GitRepoPusher.props(grangerRepo), "gitPusher")

      grangerRepo.loadData().unsafeRunSync() match {
        case LoadDataSuccess =>
          ctx.log.info("Done")
          databaseReadyBehaviour(grangerRepo, gitRepoPusher, notificationActor, clock)
        case LoadDataFailure(repoState) =>
          repoState match {
            case EmptyRepo =>
              recoverReminders(grangerRepo, notificationActor, noopActor) match {
                case Right(_) => databaseReadyBehaviour(grangerRepo, gitRepoPusher, notificationActor, clock)
                case Left(EmptyRepo) =>
                  grangerRepo.setUpRepo().unsafeRunSync()
                  databaseReadyBehaviour(grangerRepo, gitRepoPusher, notificationActor, clock)
              }
            case _ =>
              ctx.log.error("Failed to load repo: " + repoState)
              Behaviors.stopped
          }
      }
    }

  private def getInformation(
                  grangerRepo: GrangerRepoType,
                  notificationActor: ActorRef[CheckReminders]): Behavior[Protocol] = Behaviors.receiveMessage {
    case FetchAllPatients(replyTo: ActorRef[List[Patient]]) =>
      grangerRepo.retrieveAllPatients().unsafeRunSync().map {
        replyTo ! _
      }
      Behaviors.same
    case LatestActivity(patientId, replyTo) =>
      replyTo ! grangerRepo.getLatestActivity(patientId)
      Behaviors.same
    case GetTreatmentNotifications(time, replyTo) =>
      notificationActor ! CheckReminders(time, replyTo)
      Behaviors.same
    case _ => Behaviors.unhandled
  }


  private[this] def submitInformation(
          grangerRepo: GrangerRepoType,
          gitRepoPusher: akka.actor.ActorRef,
          notificationActor: ActorRef[reminders.Protocol],
          clock: Clock): Behavior[Protocol] = Behaviors.setup[Protocol] { ctx =>
    val missingFeatureActor = ctx.spawn(Behaviors.ignore[Any], "MissingFeaturesDungeon")
    Behaviors.receiveMessage[Protocol] {
      case AddPatient(patient, replyTo) =>
        gitRepoPusher ! GitRepoPusher.PushChanges
        replyTo ! grangerRepo.addPatient(patient).unsafeRunSync()
        Behaviors.same
      case AddToothInformation(request, replyTo) =>
        request.medicament.foreach { med =>
          ctx.system.eventStream ! Publish(MedicamentSeen(med))
        }
        replyTo ! grangerRepo.addToothInfo(request).map(_.unsafeRunSync())
        Behaviors.same

      case StartTreatment(patientId, toothId, category, replyTo) =>
        gitRepoPusher ! GitRepoPusher.PushChanges
        replyTo ! grangerRepo.startTreatment(patientId, toothId, category).map(_.unsafeRunSync())
        Behaviors.same
      case FinishTreatment(patientId, toothId, finishTime, replyTo) =>
        gitRepoPusher ! GitRepoPusher.PushChanges
        val outcome = grangerRepo.finishTreatment(patientId, toothId, finishTime).map(_.unsafeRunSync())
        outcome.foreach(_ => {
          notificationActor ! SetReminder(finishTime, finishTime.plusMonths(6), patientId, missingFeatureActor)
        })
        replyTo ! outcome
        Behaviors.same
      case DeleteTreatment(patientId, toothId, timestamp, replyTo) =>
        gitRepoPusher ! GitRepoPusher.PushChanges
        replyTo ! grangerRepo.deleteTreatment(patientId, toothId, timestamp).map(_.unsafeRunSync())
        Behaviors.same
      case DeletePatient(patientId, replyTo) =>
        gitRepoPusher ! GitRepoPusher.PushChanges
        val outcome = grangerRepo.deletePatient(patientId)
          .map(_.map(_ => Success).left.map(_ => Failure(s"Failed to delete patient ${patientId}"))).unsafeRunSync()
        replyTo ! outcome.merge
        Behaviors.same

      case ModifyReminderRQ(reminderTimestamp, snoozeTo, patientId, replyTo) =>
        notificationActor ! ModifyReminder(reminderTimestamp, snoozeTo, patientId, replyTo)
        Behaviors.same
      case StopReminder(timestamp, patientId, replyTo: ActorRef[DeletedAck]) =>
        notificationActor ! DeleteReminder(timestamp, patientId, ZonedDateTime.now(clock), replyTo)
        Behaviors.same
      case FetchAllPatientReminders(patientId, replyTo) =>
        notificationActor ! PatientReminders(patientId, replyTo)
        Behaviors.same
      case _ =>
        Behaviors.unhandled
    }
  }


  private def databaseReadyBehaviour(grangerRepo: GrangerRepo[Map[PatientId, Patient], IO],
                                     gitRepoPusher: akka.actor.ActorRef,
                                     notificationsActor: ActorRef[reminders.Protocol],
                                     clock: Clock
  ): Behavior[Protocol] =
    getInformation(grangerRepo, notificationsActor) orElse
      submitInformation(grangerRepo, gitRepoPusher, notificationsActor, clock)


  private def recoverReminders(
                              grangerRepo: GrangerRepo[Map[PatientId, Patient], IO],
                              notificationActor: ActorRef[reminders.Protocol],
                              noop: ActorRef[Any]) = {
    grangerRepo.retrieveAllPatients().unsafeRunSync().map {
      patients => patients.foreach(patient =>
        patient.dentalChart.teeth.foreach(_.treatments.foreach(
          _.dateCompleted.foreach(completedDate =>
            notificationActor ! SetReminder(completedDate, completedDate.plusMonths(6), patient.patientId, noop))
        )
        )
      )
    }
  }


}

sealed trait Protocol

case class FetchAllPatients(replyTo: ActorRef[List[Patient]]) extends Protocol

case class AddPatient(patient: Patient, replyTo: ActorRef[Patient]) extends Protocol

case class LatestActivity(patientId: PatientId, replyTo: ActorRef[Map[Int, List[Activity]]]) extends Protocol

case class AddToothInformation(
                addToothInformationRequest: AddToothInformationRequest,
                replyTo: ActorRef[Either[InvalidData, Patient]]) extends Protocol

private[granger] case class InitRepo(remoteRepo: RemoteRepo) extends Protocol

case class StartTreatment(patientId: PatientId,
                          toothId: Int,
                          category: TreatmentCategory, replyTo: ActorRef[Either[InvalidData, Patient]]) extends Protocol

case class DeleteTreatment(
      patientId: PatientId, toothId: Int,
      createdOn: ZonedDateTime,
      replyTo: ActorRef[Either[InvalidData, Patient]]) extends Protocol

case class FinishTreatment(
                            patientId: PatientId,
                            toothId: Int, finishedOn: ZonedDateTime,
                            replyTo: ActorRef[Either[InvalidData, Patient]]) extends Protocol {
  require(finishedOn.getOffset.equals(ZoneOffset.UTC))
}


sealed trait LoadDataOutcome

case object LoadDataSuccess extends LoadDataOutcome

case class LoadDataFailure(repoState: RepoErrorState) extends LoadDataOutcome

case object RememberedData

case class DeletePatient(patientId: PatientId, replyTo: ActorRef[CommandOutcome]) extends Protocol

case class GetTreatmentNotifications(time: ZonedDateTime, replyTo: ActorRef[Notify]) extends Protocol

sealed trait CommandOutcome

case object Success extends CommandOutcome

case class Failure(reason: String) extends CommandOutcome

case class StopReminder(
                         timestamp: ZonedDateTime,
                         patientId: PatientId,
                         replyTo: ActorRef[DeletedAck]) extends Protocol

case class ModifyReminderRQ(
                             reminderTimestamp: ZonedDateTime,
                             snoozeTo: ZonedDateTime,
                             patientId: PatientId,
                             replyTo: ActorRef[SnoozeAck]) extends Protocol

case class FetchAllPatientReminders(patientId: PatientId, replyTo: ActorRef[AllPatientReminders]) extends Protocol
