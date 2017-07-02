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
        Post("/update", request.asJson) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments.head.roots shouldBe List(Root("MB2", 19, "F2"))
        }
      }
    }
  }
}
