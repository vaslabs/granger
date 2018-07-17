package org.vaslabs.granger.reminders

import java.time.ZonedDateTime
import java.util.UUID

import akka.actor.{Actor, Props}
import cats.data.NonEmptyList
import monocle.macros.Lenses

class RCTReminderActor extends Actor{
  import RCTReminderActor.Protocol.External._
  import RCTReminderActor.Protocol.Internal._
  override def receive: Receive = {
    case SetReminder(remindOn, externalReference) =>
      context.become(behaviourWithReminders(Set(Reminder(remindOn, externalReference))))
      sender() ! ReminderSetAck(externalReference, remindOn)
  }

  private[this] def behaviourWithReminders(reminders: Set[Reminder]): Receive = {
    case CheckReminders(now) =>
      val remindersToSend = NonEmptyList.fromList(reminders.toList.filter(_.remindOn.compareTo(now) <= 0))
      remindersToSend.map(_.map(r => Notification(r.remindOn, r.externalReference))).map(Notify)
        .foreach(sender ! _)
    case SetReminder(remindOn, externalReference) =>
      val allReminders = reminders + Reminder(remindOn, externalReference)
      context.become(behaviourWithReminders(allReminders))
      sender() ! ReminderSetAck(externalReference, remindOn)
    case ModifyReminder(snoozeTo, externalReference) =>
      reminders.find(_.externalReference == externalReference).map(r => Reminder.remindOn.set(snoozeTo)(r))
        .foreach {
          modifiedReminder =>
            context.become(behaviourWithReminders((reminders - modifiedReminder) + (modifiedReminder)))
            sender() ! SnoozeAck(modifiedReminder.externalReference, modifiedReminder.remindOn)
        }
  }
}

object RCTReminderActor {

  def props: Props = Props(new RCTReminderActor)

  object Protocol {
    object External {
      case class SetReminder(remindOn: ZonedDateTime, externalReference: UUID)
      case class ModifyReminder(snoozeTo: ZonedDateTime, externalReference: UUID)
      case class DeleteReminder(externalReference: UUID)

      case class ReminderSetAck(externalId: UUID, notificationTime: ZonedDateTime)
      case class SnoozeAck(externalReference: UUID, movedAt: ZonedDateTime)
      case class DeletedAck(externalRefernce: UUID)

      case class Notification(notificationTime: ZonedDateTime, externalReference: UUID)

      case class Notify(notifications: NonEmptyList[Notification])

    }
    private[reminders] object Internal {
      case class CheckReminders(now: ZonedDateTime)
      @Lenses case class Reminder(remindOn: ZonedDateTime, externalReference: UUID) {
        override def hashCode(): Int = externalReference.hashCode()

        override def equals(obj: scala.Any): Boolean = obj match {
          case Reminder(_, otherExternalReference) => externalReference.equals(otherExternalReference)
          case _ => super.equals(obj)
        }
      }
    }
  }
}
