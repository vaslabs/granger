package org.vaslabs.granger.reminders

import java.time._

import akka.testkit.TestActorRef
import org.scalatest.WordSpecLike
import org.vaslabs.granger.AkkaBaseSpec
import org.vaslabs.granger.modelv2.PatientId
import org.vaslabs.granger.reminders.RCTReminderActor.Protocol.External._
import scala.concurrent.duration._

class RCTReminderActorSpec extends AkkaBaseSpec("RCTRemindersSpec") with WordSpecLike
{

  val TestClock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

  "a reminder actor spec" can {
    val rctReminderActor = TestActorRef[RCTReminderActor](RCTReminderActor.props(tmpDir))
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
      expectMsg(Notify(List(Notification(dateStarted, expectedNotificationTime, externalId))))
    }

    "publish multiple notifications" in {

      rctReminderActor ! SetReminder(dateStarted, ZonedDateTime.now(TestClock).plusMonths(6), secondExternalId)
      expectMsg(ReminderSetAck(secondExternalId, dateStarted, expectedNotificationTime))
      rctReminderActor ! CheckReminders(ZonedDateTime.now(TestClock).plusMonths(6))
      expectMsg(Notify(List(
        Notification(dateStarted, expectedNotificationTime, externalId),
        Notification(dateStarted, expectedNotificationTime, secondExternalId)))
      )
    }

    "gives all notifications of specific user" in {
      rctReminderActor ! RCTReminderActor.Protocol.External.PatientReminders(externalId)
      expectMsg(RCTReminderActor.Protocol.External.AllPatientReminders(
        List(
          Notification(dateStarted, expectedNotificationTime, externalId)
        )
      ))
    }

    "snooze notifications" in {
      val newTime = ZonedDateTime.now(TestClock).plusMonths(7)
      rctReminderActor ! ModifyReminder(dateStarted, ZonedDateTime.now(TestClock).plusMonths(7), externalId)
      expectMsg(SnoozeAck(externalId, dateStarted, newTime))
      rctReminderActor ! CheckReminders(ZonedDateTime.now(TestClock).plusMonths(6))
      expectMsg(Notify(List(
        Notification(dateStarted, expectedNotificationTime, secondExternalId)))
      )
    }

    "stop notifications" in {
      rctReminderActor ! DeleteReminder(dateStarted, externalId, ZonedDateTime.now(clock).plusMonths(8))
      expectMsg(DeletedAck(dateStarted, externalId))
      rctReminderActor ! CheckReminders(ZonedDateTime.now(TestClock).plusMonths(7))
      expectMsg(
        Notify(List(
          Notification(dateStarted, expectedNotificationTime, secondExternalId)))
      )
    }

    "deleting reminders persists" in {
      rctReminderActor ! SetReminder(dateStarted, ZonedDateTime.now(clock).plusMonths(1), externalId)
      expectNoMessage(1 second)
    }

    "even after restarts" in {
      system.stop(rctReminderActor)
      val recoveredActor = TestActorRef[RCTReminderActor](RCTReminderActor.props(tmpDir))
      recoveredActor ! SetReminder(dateStarted, ZonedDateTime.now(clock).plusMonths(1), externalId)
      expectNoMessage(1 second)
    }
  }


}
