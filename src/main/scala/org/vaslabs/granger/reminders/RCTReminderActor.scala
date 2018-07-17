package org.vaslabs.granger.reminders

import java.time.ZonedDateTime
import java.util.Objects

import akka.actor.{Actor, Props}
import cats.data.NonEmptyList
import cats.kernel.Hash
import monocle.macros.Lenses
import org.vaslabs.granger.modelv2.PatientId

import scala.runtime.Statics
import scala.util.hashing.Hashing

class RCTReminderActor extends Actor{
  import RCTReminderActor.Protocol.External._
  import RCTReminderActor.Protocol.Internal._
  override def receive: Receive = {
    case SetReminder(submitted, remindOn, externalReference) =>
      context.become(behaviourWithReminders(Set(Reminder(submitted, remindOn, externalReference))))
      sender() ! ReminderSetAck(externalReference, submitted, remindOn)
  }

  private[this] def behaviourWithReminders(reminders: Set[Reminder]): Receive = {
    case CheckReminders(now) =>
      val remindersToSend = NonEmptyList.fromList(reminders.toList.filter(_.remindOn.compareTo(now) <= 0))
      remindersToSend.map(_.map(r => Notification(r.submitted, r.remindOn, r.externalReference))).map(Notify)
        .foreach(sender ! _)
    case SetReminder(submitted, remindOn, externalReference) =>
      val allReminders = reminders + Reminder(submitted, remindOn, externalReference)
      context.become(behaviourWithReminders(allReminders))
      sender() ! ReminderSetAck(externalReference, submitted, remindOn)
    case ModifyReminder(timestamp, snoozeTo, externalReference) =>
      reminders.find(r => r.externalReference == externalReference && r.submitted == timestamp).map(r => Reminder.remindOn.set(snoozeTo)(r))
        .foreach {
          modifiedReminder =>
            context.become(behaviourWithReminders((reminders - modifiedReminder) + (modifiedReminder)))
            sender() ! SnoozeAck(modifiedReminder.externalReference, timestamp, modifiedReminder.remindOn)
        }
    case DeleteReminder(timestamp, externalReference) =>
      val remainingReminders = reminders.filterNot(r => r.externalReference == externalReference && r.submitted == timestamp)
      context.become(behaviourWithReminders(remainingReminders))
      sender() ! DeletedAck(timestamp, externalReference)
  }
}

object RCTReminderActor {

  def props: Props = Props(new RCTReminderActor)

  object Protocol {
    object External {
      case class SetReminder(submitted: ZonedDateTime, remindOn: ZonedDateTime, externalReference: PatientId)
      case class ModifyReminder(reminderTimestamp: ZonedDateTime, snoozeTo: ZonedDateTime, externalReference: PatientId)
      case class DeleteReminder(reminderTimestamp: ZonedDateTime, externalReference: PatientId)

      case class ReminderSetAck(externalId: PatientId, timestamp: ZonedDateTime, notificationTime: ZonedDateTime)
      case class SnoozeAck(externalReference: PatientId, timestamp: ZonedDateTime, movedAt: ZonedDateTime)
      case class DeletedAck(timestamp: ZonedDateTime, externalRefernce: PatientId)

      case class Notification(timestamp: ZonedDateTime, notificationTime: ZonedDateTime, externalReference: PatientId)

      case class Notify(notifications: NonEmptyList[Notification])

    }
    private[reminders] object Internal {
      case class CheckReminders(now: ZonedDateTime)
      @Lenses case class Reminder(submitted: ZonedDateTime, remindOn: ZonedDateTime, externalReference: PatientId) {
        override def hashCode(): Int = Objects.hash(submitted.asInstanceOf[Object], externalReference.asInstanceOf[Object])

        override def equals(obj: scala.Any): Boolean = obj match {
          case Reminder(submitted, _, otherExternalReference) =>
            externalReference.equals(otherExternalReference) && submitted == this.submitted
          case _ => super.equals(obj)
        }
      }
    }
  }
}
