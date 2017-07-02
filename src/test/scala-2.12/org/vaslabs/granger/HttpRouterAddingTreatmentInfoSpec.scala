package org.vaslabs.granger

import java.time.ZonedDateTime

import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.vaslabs.granger.PatientManager.StartTreatment
import org.vaslabs.granger.comms.api.model.AddToothInformationRequest
import org.vaslabs.granger.modelv2._
import io.circe.syntax._
import io.circe.generic.auto._

/**
  * Created by vnicolaou on 02/07/17.
  */
class HttpRouterAddingTreatmentInfoSpec extends BaseSpec with FailFastCirceSupport{

  "when adding tooth information, the information" should "go to the last open treatment" in {
    withHttpRouter(system, config) {
      httpRouter => {
        Post("/api", Patient(PatientId(0), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart(List.empty))) ~> httpRouter.routes ~> check {
          responseAs[Patient] shouldBe Patient(PatientId(1), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart.emptyChart())
        }
        Post("/treatment/start", StartTreatment(PatientId(1), 11, "A treatment")) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), None, "A treatment"))
        }
        val request = AddToothInformationRequest(PatientId(1), 11, None, None, Some(List(Root("MB2", 19, "F2"))), None).asJson
        Post("/update", request) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments.head.roots shouldBe List(Root("MB2", 19, "F2"))
        }
        Post("/treatment/finish", StartTreatment(PatientId(1), 11, "A treatment")) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments.head.dateCompleted shouldBe Some(ZonedDateTime.now(clock))
        }
        val anotherRequest = AddToothInformationRequest(PatientId(1), 11, None, None, Some(List(Root("MB", 19, "F1"))), None).asJson
        Post("/update", anotherRequest) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments.head.roots shouldBe List(Root("MB2", 19, "F2"))
        }
        Post("/treatment/start", StartTreatment(PatientId(1), 11, "B treatment")) ~> httpRouter.routes ~> check {
          val treatments = responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments
          treatments.head shouldBe Treatment(ZonedDateTime.now(clock), None, "B treatment")
          treatments.apply(1).roots shouldBe List(Root("MB2", 19, "F2"))
          treatments.head.roots shouldBe List.empty
        }
        Post("/update", anotherRequest) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments.head.roots shouldBe List(Root("MB", 19, "F1"))
        }
      }
    }
  }
}
