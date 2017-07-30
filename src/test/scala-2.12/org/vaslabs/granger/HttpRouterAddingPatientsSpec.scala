package org.vaslabs.granger

import java.time.ZonedDateTime

import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.Id
import org.vaslabs.granger.modelv2.{DentalChart, Patient, PatientId}
import org.vaslabs.granger.v2json._
import io.circe.generic.auto._
import org.scalatest.Matchers
/**
  * Created by vnicolaou on 28/06/17.
  */
class HttpRouterAddingPatientsSpec extends HttpBaseSpec with ScalatestRouteTest with Matchers{

  import v2json._

  "adding a new patient" should "persist across restarts" in {
    withHttpRouter[Id](system, config) {
      httpRouter => {
        Get("/api") ~> httpRouter.routes ~> check {
          responseAs[List[Patient]].size shouldBe 0
        }
        Post("/api", Patient(PatientId(0), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart(List.empty))) ~> httpRouter.routes ~> check {
          responseAs[Patient] shouldBe Patient(PatientId(1), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart.emptyChart())
        }
        import scala.collection.JavaConverters._
        git.log().call().asScala.size shouldBe 2
        Get("/api") ~> httpRouter.routes ~> check {
          responseAs[List[Patient]] shouldBe List(Patient(PatientId(1), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart.emptyChart()))
        }
        gitRepo.getState().toOption.get.get(PatientId(1)).get shouldBe Patient(PatientId(1), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart.emptyChart())
      }
    }
  }
}
