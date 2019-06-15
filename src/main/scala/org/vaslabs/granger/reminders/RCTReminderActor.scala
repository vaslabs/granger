package org.vaslabs.granger.reminders

import java.io.File
import java.time.ZonedDateTime
import java.util.Objects

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import monocle.macros.Lenses
import org.eclipse.jgit.api.Git
import org.vaslabs.granger.modelv2.PatientId
import org.vaslabs.granger.repo.git.{EmptyProvider, GitRepo}
import org.vaslabs.granger.repo.{EmptyRepo, RepoErrorState}


object RCTReminderActor {
  import io.circe.java8.time._
  import io.circe.generic.auto._
  implicit val emptyNotificationsProvider: EmptyProvider[List[Reminder]] = () => List.empty

  def behaviour(repoLocation: String)(implicit git: Git): Behavior[Protocol] = Behaviors.setup { ctx =>
    val notificationsRepo: GitRepo[List[Reminder]] =
      new GitRepo[List[Reminder]](new File(repoLocation), "notification_changes.json")

    notificationsRepo.getState().left.map(err => ctx.self ! LoadingError(err)).map(ReminderState).foreach(ctx.self ! _)

    Behaviors.receive {
      case (ctx, ReminderState(reminders)) =>
        ctx.log.info("Notification system for RCT reminders is initialised")
        behaviourWithReminders(reminders.toSet, notificationsRepo)

      case (ctx, LoadingError(EmptyRepo)) =>
        notificationsRepo.saveNew()
        ctx.log.info("Notification system for RCT reminders is initialised for the first time")
        behaviourWithReminders(Set.empty, notificationsRepo)
      case (ctx, LoadingError(genericError)) =>
        ctx.log.info("Unhandled error {}, reminders are disabled", genericError)
        Behaviors.ignore
      case _ => Behaviors.ignore
    }
  }


  private[this] def behaviourWithReminders(reminders: Set[Reminder], notificationsRepo: GitRepo[List[Reminder]]): Behavior[Protocol] = Behaviors.receiveMessage {
    case CheckReminders(now, replyTo: ActorRef[Notify]) =>
      val remindersToSend = reminders.filter(_.remindOn.compareTo(now) <= 0).filterNot(_.deletedOn.isDefined)
      val notify = Notify(remindersToSend.map(r => Notification(r.submitted, r.remindOn, r.externalReference)).toList)
      replyTo ! notify
      Behaviors.same
    case SetReminder(submitted, remindOn, externalReference, replyTo: ActorRef[ReminderSetAck]) =>
      val newReminder = Reminder(submitted, remindOn, externalReference)
      val allReminders = reminders + newReminder
      replyTo ! ReminderSetAck(externalReference, submitted, remindOn)
      behaviourWithReminders(allReminders, notificationsRepo)
    case ModifyReminder(timestamp, snoozeTo, externalReference, replyTo: ActorRef[SnoozeAck]) =>
      val savedReminders = for {
        reminderToModify <- reminders.find(r =>
          r.externalReference == externalReference &&
            r.submitted.compareTo(timestamp) == 0 &&
            r.deletedOn.isEmpty)
        modifiedReminder = Reminder.remindOn.set(snoozeTo)(reminderToModify)
        newReminders = (reminders - reminderToModify) + (modifiedReminder)
        _ <- notificationsRepo.save(
          s"Saving reminder modification $timestamp of patient id $externalReference",
          newReminders.toList).toOption
        _ = replyTo ! SnoozeAck(modifiedReminder.externalReference, timestamp, modifiedReminder.remindOn)
      } yield newReminders

      savedReminders.map(behaviourWithReminders(_, notificationsRepo)).getOrElse(Behaviors.same)

    case DeleteReminder(timestamp, externalReference, deletionTime, replyTo: ActorRef[DeletedAck]) =>
      val savedReminders = for {
        reminderToDelete <- reminders.find(r => r.externalReference == externalReference && r.submitted == timestamp)
        reminderAfterDeletion = Reminder.deletedOn.set(Some(deletionTime))(reminderToDelete)
        newReminders = (reminders - reminderToDelete) + reminderAfterDeletion
        _ <- notificationsRepo.save(s"Stopping reminder $timestamp of patient id $externalReference", newReminders.toList).toOption

      } yield newReminders
      savedReminders.foreach(_ => replyTo ! DeletedAck(timestamp, externalReference))
      savedReminders.map(behaviourWithReminders(_, notificationsRepo)).getOrElse(Behaviors.same)

    case PatientReminders(patientId, replyTo: ActorRef[AllPatientReminders]) =>
      replyTo ! AllPatientReminders(
        reminders.filter(_.externalReference == patientId).filter(_.deletedOn.isEmpty)
          .map(r => Notification(r.submitted, r.remindOn, r.externalReference)).toList
      )
      Behaviors.same
  }
}



sealed trait Protocol

case class SetReminder(
         submitted: ZonedDateTime,
         remindOn: ZonedDateTime,
         externalReference: PatientId,
         actorRef: ActorRef[ReminderSetAck]) extends Protocol

case class ModifyReminder(
         reminderTimestamp: ZonedDateTime,
         snoozeTo: ZonedDateTime,
         externalReference: PatientId,
         replyTo: ActorRef[SnoozeAck]) extends Protocol

case class DeleteReminder(reminderTimestamp: ZonedDateTime, externalReference: PatientId, deletionTime: ZonedDateTime, actorRef: ActorRef[DeletedAck]) extends Protocol

case class ReminderSetAck(externalId: PatientId, timestamp: ZonedDateTime, notificationTime: ZonedDateTime)

case class SnoozeAck(externalReference: PatientId, timestamp: ZonedDateTime, movedAt: ZonedDateTime)

case class DeletedAck(timestamp: ZonedDateTime, externalRefernce: PatientId)

case class Notification(timestamp: ZonedDateTime, notificationTime: ZonedDateTime, externalReference: PatientId)

case class Notify(notifications: List[Notification]) extends Protocol

case class CheckReminders(now: ZonedDateTime, replyTo: ActorRef[Notify]) extends Protocol

case class PatientReminders(externalId: PatientId, replyTo: ActorRef[AllPatientReminders]) extends Protocol

case class AllPatientReminders(notifications: List[Notification])


@Lenses private[reminders] case class Reminder(
                                                submitted: ZonedDateTime,
                                                remindOn: ZonedDateTime,
                                                externalReference: PatientId,
                                                deletedOn: Option[ZonedDateTime] = None)  extends Protocol {
  override def hashCode(): Int = Objects.hash(submitted.asInstanceOf[Object], externalReference.asInstanceOf[Object])

  override def equals(obj: scala.Any): Boolean = obj match {
    case Reminder(submitted, _, otherExternalReference, _) =>
      externalReference.equals(otherExternalReference) && submitted == this.submitted
    case _ => false
  }

  override def canEqual(that: Any): Boolean = that.isInstanceOf[Reminder]
}

private[reminders] case class ReminderState(reminders: List[Reminder]) extends Protocol

private[reminders] case class LoadingError(repoErrorState: RepoErrorState) extends Protocol
