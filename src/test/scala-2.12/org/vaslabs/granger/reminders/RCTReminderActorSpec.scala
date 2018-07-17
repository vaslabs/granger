package org.vaslabs.granger.reminders

import java.time._

import akka.testkit.TestActorRef
import cats.data.NonEmptyList
import org.scalatest.WordSpecLike
import org.vaslabs.granger.AkkaBaseSpec
import org.vaslabs.granger.modelv2.PatientId
import org.vaslabs.granger.reminders.RCTReminderActor.Protocol.External._
import org.vaslabs.granger.reminders.RCTReminderActor.Protocol.Internal._
import scala.concurrent.duration._

class RCTReminderActorSpec extends AkkaBaseSpec("RCTRemindersSpec") with WordSpecLike
{

  val TestClock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

  "a reminder actor spec" can {
    val rctReminderActor = TestActorRef[RCTReminderActor](RCTReminderActor.props)
    val externalId = PatientId(1)
    val dateStarted = ZonedDateTime.now(clock)
    val secondExternalId = PatientId(2)
    val expectedNotificationTime = ZonedDateTime.now(TestClock).plusMonths(6)
    "accept reminders" in {
      rctReminderActor ! SetReminder(dateStarted, ZonedDateTime.now(TestClock).plusMonths(6), externalId)
      expectMsg(ReminderSetAck(externalId, dateStarted, expectedNotificationTime))
    }

    "publish a notification when time comes" in {
      rctReminderActor ! CheckReminders(ZonedDateTime.now(TestClock).plusMonths(6))
      expectMsg(Notify(NonEmptyList.of(Notification(dateStarted, expectedNotificationTime, externalId))))
    }

    "publish multiple notifications" in {

      rctReminderActor ! SetReminder(dateStarted, ZonedDateTime.now(TestClock).plusMonths(6), secondExternalId)
      expectMsg(ReminderSetAck(secondExternalId, dateStarted, expectedNotificationTime))
      rctReminderActor ! CheckReminders(ZonedDateTime.now(TestClock).plusMonths(6))
      expectMsg(Notify(NonEmptyList.of(
        Notification(dateStarted, expectedNotificationTime, externalId),
        Notification(dateStarted, expectedNotificationTime, secondExternalId)))
      )
    }

    "snooze notifications" in {
      val newTime = ZonedDateTime.now(TestClock).plusMonths(7)
      rctReminderActor ! ModifyReminder(dateStarted, ZonedDateTime.now(TestClock).plusMonths(7), externalId)
      expectMsg(SnoozeAck(externalId, dateStarted, newTime))
      rctReminderActor ! CheckReminders(ZonedDateTime.now(TestClock).plusMonths(6))
      expectMsg(Notify(NonEmptyList.of(
        Notification(dateStarted, expectedNotificationTime, secondExternalId)))
      )
    }

    "stop notifications" in {
      rctReminderActor ! DeleteReminder(dateStarted, externalId, ZonedDateTime.now(clock).plusMonths(8))
      expectMsg(DeletedAck(dateStarted, externalId))
      rctReminderActor ! CheckReminders(ZonedDateTime.now(TestClock).plusMonths(7))
      expectMsg(
        Notify(NonEmptyList.of(
          Notification(dateStarted, expectedNotificationTime, secondExternalId)))
      )
    }

    "deleting reminders persists" in {
      rctReminderActor ! SetReminder(dateStarted, ZonedDateTime.now(clock).plusMonths(1), externalId)
      expectNoMessage(1 second)
    }
  }


}
