package org.vaslabs.granger

import java.time.ZonedDateTime

import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.Id
import org.vaslabs.granger.PatientManager.StartTreatment
import org.vaslabs.granger.comms.api.model.AddToothInformationRequest
import org.vaslabs.granger.modelv2._
import org.vaslabs.granger.v2json._
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.Matchers
import org.vaslabs.granger.modeltreatments.{RootCanalTreatment}

/**
  * Created by vnicolaou on 02/07/17.
  */
class HttpRouterAddingTreatmentInfoSpec extends HttpBaseSpec with ScalatestRouteTest with Matchers{

  "when adding tooth information, the information" should "go to the last open treatment" in {
    withHttpRouter[Id](system, config) {
      httpRouter => {
        Post("/api", Patient(PatientId(0), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart(List.empty))) ~> httpRouter.routes ~> check {
          responseAs[Patient] shouldBe Patient(PatientId(1), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart.emptyChart())
        }
        Post("/treatment/start", StartTreatment(PatientId(1), 11, RootCanalTreatment())) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), None, RootCanalTreatment()))
        }
        val request = AddToothInformationRequest(PatientId(1), 11, None, None, Some(List(Root("MB2", 19, "F2"))), None, None, ZonedDateTime.now(clock)).asJson
        println(request.noSpaces)
        Post("/update", request) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments.head.roots shouldBe List(Root("MB2", 19, "F2"))
        }
        Post("/treatment/finish", StartTreatment(PatientId(1), 11, RootCanalTreatment())) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments.head.dateCompleted shouldBe
            Some(ZonedDateTime.now(clock))
        }
      }
    }
  }
}
