package org.vaslabs.granger.reminders

import java.time._
import java.util.UUID

import akka.testkit.TestActorRef
import cats.data.NonEmptyList
import org.scalatest.WordSpecLike
import org.vaslabs.granger.AkkaBaseSpec
import org.vaslabs.granger.reminders.RCTReminderActor.Protocol.External._
import org.vaslabs.granger.reminders.RCTReminderActor.Protocol.Internal._

class RCTReminderActorSpec extends AkkaBaseSpec("RCTRemindersSpec") with WordSpecLike
{

  val TestClock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

  "a reminder actor spec" can {
    val rctReminderActor = TestActorRef[RCTReminderActor](RCTReminderActor.props)
    val externalId = UUID.randomUUID()
    val secondExternalId = UUID.randomUUID()
    val expectedNotificationTime = ZonedDateTime.now(TestClock).plusMonths(6)
    "accept reminders" in {
      rctReminderActor ! SetReminder(ZonedDateTime.now(TestClock).plusMonths(6), externalId)
      expectMsg(ReminderSetAck(externalId, expectedNotificationTime))
    }

    "publish a notification when time comes" in {
      rctReminderActor ! CheckReminders(ZonedDateTime.now(TestClock).plusMonths(6))
      expectMsg(Notify(NonEmptyList.of(Notification(expectedNotificationTime, externalId))))
    }

    "publish multiple notifications" in {

      rctReminderActor ! SetReminder(ZonedDateTime.now(TestClock).plusMonths(6), secondExternalId)
      expectMsg(ReminderSetAck(secondExternalId, expectedNotificationTime))
      rctReminderActor ! CheckReminders(ZonedDateTime.now(TestClock).plusMonths(6))
      expectMsg(Notify(NonEmptyList.of(
        Notification(expectedNotificationTime, externalId),
        Notification(expectedNotificationTime, secondExternalId)))
      )
    }

    "snooze notifications" in {
      val newTime = ZonedDateTime.now(TestClock).plusMonths(7)
      rctReminderActor ! ModifyReminder(ZonedDateTime.now(TestClock).plusMonths(7), externalId)
      expectMsg(SnoozeAck(externalId, newTime))
      rctReminderActor ! CheckReminders(ZonedDateTime.now(TestClock).plusMonths(6))
      expectMsg(Notify(NonEmptyList.of(
        Notification(expectedNotificationTime, secondExternalId)))
      )
    }

//    "stop notifications" in {
//      rctReminderActor ! DeleteReminder(externalId)
//      expectMsg(DeletedAck(externalId))
//
//    }
  }


}
