package org.vaslabs.granger

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.Id
import org.vaslabs.granger.comms.api.model.{AddToothInformationRequest, AutocompleteSuggestions}
import org.vaslabs.granger.modelv2._
import org.vaslabs.granger.v2json._
import io.circe.generic.auto._
import org.scalatest.Matchers
import org.vaslabs.granger.comms.UserApi
import org.vaslabs.granger.modeltreatments.RootCanalTreatment
import org.vaslabs.granger.reminders._

/**
  * Created by vnicolaou on 02/07/17.
  */
class HttpRouterAddingTreatmentInfoSpec extends HttpBaseSpec with ScalatestRouteTest with Matchers{

  val now = ZonedDateTime.now(clock)

  "when adding tooth information, the information" should
  "go to the last open treatment" in
  {
    withHttpRouter[Id](config) {
      httpRouter => {

        Post("/api", withNewPatient()) ~> httpRouter.routes ~> check {
          responseAs[Patient] shouldBe withPatient(PatientId(1))
        }

        Post("/treatment/start", UserApi.StartTreatment(PatientId(1), 11, RootCanalTreatment())) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe
            List(withOpenTreatment())
        }

        val request = AddToothInformationRequest(
          PatientId(1), 11, Some(Medicament("CaO2", ZonedDateTime.now(clock))),
          None, Some(List(Root("MB2", 19, "F2"))),
          None, None, ZonedDateTime.now(clock)
        )

        Post("/update", request) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get
            .treatments.head.roots shouldBe
          List(Root("MB2", 19, "F2"))
        }

        Get("/api/remember") ~> httpRouter.routes ~> check {
          responseAs[AutocompleteSuggestions] shouldBe AutocompleteSuggestions(List("CaO2"))
        }

        val finishedAt = ZonedDateTime.now(clock).plusDays(1)
        Post("/treatment/finish", UserApi.FinishTreatment(PatientId(1), 11, finishedAt)) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments.head.dateCompleted shouldBe Some(finishedAt)
        }
        Get(s"/treatment/notifications/${now.plusMonths(6).plusDays(1)
          .format(DateTimeFormatter.ISO_ZONED_DATE_TIME)}") ~> httpRouter.routes ~> check {
          responseAs[Notify] shouldBe
            Notify(List(Notification(
              finishedAt, finishedAt.plusMonths(6), PatientId(1))))
        }
        Get("/allreminders/1") ~> httpRouter.routes ~> check {
          responseAs[AllPatientReminders] shouldBe
            AllPatientReminders(
              List(Notification(
                finishedAt, finishedAt.plusMonths(6), PatientId(1)))
            )
        }
        Post("/treatment/notifications", UserApi.ModifyReminder(finishedAt, finishedAt.plusMonths(7), PatientId(1))) ~> httpRouter.routes ~> check {
          responseAs[SnoozeAck] shouldBe
            SnoozeAck(PatientId(1), finishedAt, finishedAt.plusMonths(7))
        }

        Delete(s"/treatment/notification/${finishedAt}?patientId=1") ~> httpRouter.routes ~> check {
          responseAs[DeletedAck] shouldBe
            DeletedAck(finishedAt, PatientId(1))
        }

        Get("/allreminders/1") ~> httpRouter.routes ~> check {
          responseAs[AllPatientReminders] shouldBe
            AllPatientReminders(
              List.empty
            )
        }
      }
    }
  }
}
