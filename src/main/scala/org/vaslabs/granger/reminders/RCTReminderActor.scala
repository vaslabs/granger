package org.vaslabs.granger.reminders

import java.io.File
import java.time.ZonedDateTime
import java.util.Objects

import akka.actor.{Actor, ActorLogging, Props, Stash}
import cats.data.NonEmptyList
import monocle.macros.Lenses
import org.eclipse.jgit.api.Git
import org.vaslabs.granger.modelv2.PatientId
import org.vaslabs.granger.repo.EmptyRepo
import org.vaslabs.granger.repo.git.{EmptyProvider, GitRepo}

class RCTReminderActor private(repoLocation: String)(implicit git: Git)
    extends Actor with Stash with ActorLogging{

  import RCTReminderActor.Protocol.External._
  import RCTReminderActor.Protocol.Internal._

  implicit val emptyNotificationsProvider: EmptyProvider[List[Reminder]] = () => List.empty
  import io.circe.java8.time._
  import io.circe.generic.auto._
  implicit val notificationsRepo: GitRepo[List[Reminder]] =
    new GitRepo[List[Reminder]](new File(repoLocation), "notification_changes.json")

  override def preStart(): Unit =
    notificationsRepo.getState().left.map(err => self ! err).map(ReminderState).foreach(self ! _)


  override def receive: Receive = {
    case ReminderState(reminders) =>
      context.become(behaviourWithReminders(reminders.toSet))
      unstashAll()
      log.info("Notification system for RCT reminders is initialised")
    case EmptyRepo =>
      notificationsRepo.saveNew()
      context.become(behaviourWithReminders(Set.empty))
      unstashAll()
      log.info("Notification system for RCT reminders is initialised for the first time")
    case other => stash()
  }

  private[this] def behaviourWithReminders(reminders: Set[Reminder]): Receive = {
    case CheckReminders(now) =>
      val remindersToSend = reminders.filter(_.remindOn.compareTo(now) <= 0).filterNot(_.deletedOn.isDefined)
      val notify = Notify(remindersToSend.map(r => Notification(r.submitted, r.remindOn, r.externalReference)).toList)
      sender ! notify
    case SetReminder(submitted, remindOn, externalReference) =>
      val newReminder = Reminder(submitted, remindOn, externalReference)
      if (!reminders.contains(newReminder)) {
        val allReminders = reminders + newReminder
        context.become(behaviourWithReminders(allReminders))
        sender() ! ReminderSetAck(externalReference, submitted, remindOn)
      }
    case ModifyReminder(timestamp, snoozeTo, externalReference) =>
      reminders.find(r => r.externalReference == externalReference && r.submitted == timestamp).map(r => Reminder.remindOn.set(snoozeTo)(r))
        .foreach {
          modifiedReminder =>
            val newReminders = (reminders - modifiedReminder) + (modifiedReminder)
            notificationsRepo.save(s"Saving reminder modification $timestamp of patient id $externalReference", newReminders.toList)
              .foreach { _ =>
                context.become(behaviourWithReminders(newReminders))
                sender() ! SnoozeAck(modifiedReminder.externalReference, timestamp, modifiedReminder.remindOn)
              }
        }
    case DeleteReminder(timestamp, externalReference, deletionTime) =>
      reminders.find(r => r.externalReference == externalReference && r.submitted == timestamp)
        .map(Reminder.deletedOn.set(Some(deletionTime))(_))
        .foreach {
          reminder =>
            val newReminders = (reminders - reminder) + (reminder)
            notificationsRepo.save(s"Stopping reminder $timestamp of patient id $externalReference", newReminders.toList)
              .foreach { _ =>
                context.become(behaviourWithReminders(newReminders))
                sender() ! DeletedAck(timestamp, externalReference)
              }
        }

  }
}

object RCTReminderActor {

  def props(repoLocation: String)(implicit git: Git): Props = Props(new RCTReminderActor(repoLocation))

  object Protocol {

    object External {

      case class SetReminder(submitted: ZonedDateTime, remindOn: ZonedDateTime, externalReference: PatientId)

      case class ModifyReminder(reminderTimestamp: ZonedDateTime, snoozeTo: ZonedDateTime, externalReference: PatientId)

      case class DeleteReminder(reminderTimestamp: ZonedDateTime, externalReference: PatientId, deletionTime: ZonedDateTime)

      case class ReminderSetAck(externalId: PatientId, timestamp: ZonedDateTime, notificationTime: ZonedDateTime)

      case class SnoozeAck(externalReference: PatientId, timestamp: ZonedDateTime, movedAt: ZonedDateTime)

      case class DeletedAck(timestamp: ZonedDateTime, externalRefernce: PatientId)

      case class Notification(timestamp: ZonedDateTime, notificationTime: ZonedDateTime, externalReference: PatientId)

      case class Notify(notifications: List[Notification])

      case class CheckReminders(now: ZonedDateTime)

    }

    private[reminders] object Internal {

      @Lenses case class Reminder(
                                   submitted: ZonedDateTime, remindOn: ZonedDateTime, externalReference: PatientId, deletedOn: Option[ZonedDateTime] = None) {
        override def hashCode(): Int = Objects.hash(submitted.asInstanceOf[Object], externalReference.asInstanceOf[Object])

        override def equals(obj: scala.Any): Boolean = obj match {
          case Reminder(submitted, _, otherExternalReference, _) =>
            externalReference.equals(otherExternalReference) && submitted == this.submitted
          case _ => super.equals(obj)
        }
      }

      case class ReminderState(reminders: List[Reminder])
    }


  }

}
