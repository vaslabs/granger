package org.vaslabs.granger

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.Id
import org.vaslabs.granger.PatientManager.StartTreatment
import org.vaslabs.granger.comms.api.model.AddToothInformationRequest
import org.vaslabs.granger.modelv2._
import org.vaslabs.granger.v2json._
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.Matchers
import org.vaslabs.granger.modeltreatments.RootCanalTreatment
import org.vaslabs.granger.reminders.RCTReminderActor
import org.vaslabs.granger.reminders.RCTReminderActor.Protocol.External._

/**
  * Created by vnicolaou on 02/07/17.
  */
class HttpRouterAddingTreatmentInfoSpec extends HttpBaseSpec with ScalatestRouteTest with Matchers{

  val now = ZonedDateTime.now(clock)

  "when adding tooth information, the information" should
  "go to the last open treatment" in
  {
    withHttpRouter[Id](system, config) {
      httpRouter => {

        Post("/api", withNewPatient()) ~> httpRouter.routes ~> check {
          responseAs[Patient] shouldBe withPatient(PatientId(1))
        }

        Post("/treatment/start", StartTreatment(PatientId(1), 11, RootCanalTreatment())) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe
            List(withOpenTreatment())
        }

        val request = AddToothInformationRequest(
          PatientId(1), 11, None, None, Some(List(Root("MB2", 19, "F2"))),
          None, None, ZonedDateTime.now(clock)
        )

        Post("/update", request) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get
            .treatments.head.roots shouldBe
          List(Root("MB2", 19, "F2"))
        }

        Post("/treatment/finish", StartTreatment(PatientId(1), 11, RootCanalTreatment())) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11)
            .get.treatments.head.dateCompleted shouldBe
          Some(now)
        }
        Get(s"/treatment/notifications/${now.plusMonths(6)
          .format(DateTimeFormatter.ISO_ZONED_DATE_TIME)}") ~> httpRouter.routes ~> check {
          responseAs[RCTReminderActor.Protocol.External.Notify] shouldBe
            Notify(List(RCTReminderActor.Protocol.External.Notification(
              now, now.plusMonths(6), PatientId(1))))
        }
        Get(s"/allreminders/1") ~> httpRouter.routes ~> check {
          responseAs[RCTReminderActor.Protocol.External.AllPatientReminders] shouldBe
            AllPatientReminders(
              List(RCTReminderActor.Protocol.External.Notification(
                now, now.plusMonths(6), PatientId(1)))
            )
        }
        Post("/treatment/notifications", ModifyReminder(now, now.plusMonths(7), PatientId(1))) ~> httpRouter.routes ~> check {
          responseAs[RCTReminderActor.Protocol.External.SnoozeAck] shouldBe
            SnoozeAck(PatientId(1), now, now.plusMonths(7))
        }
        Delete(s"/treatment/notification/${now}?patientId=1") ~> httpRouter.routes ~> check {
          responseAs[RCTReminderActor.Protocol.External.DeletedAck] shouldBe
            DeletedAck(now, PatientId(1))
        }
        Get(s"/allreminders/1") ~> httpRouter.routes ~> check {
          responseAs[RCTReminderActor.Protocol.External.AllPatientReminders] shouldBe
            AllPatientReminders(
              List.empty
            )
        }
      }
    }
  }
}
