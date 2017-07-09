package org.vaslabs.granger

import java.time.ZonedDateTime

import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.vaslabs.granger.modelv2.{DentalChart, Patient, PatientId}

import scala.concurrent.Future
import org.vaslabs.granger.v2json._
import io.circe.generic.auto._
/**
  * Created by vnicolaou on 28/06/17.
  */
class HttpRouterAddingPatientsSpec extends BaseSpec with FailFastCirceSupport {

  "adding a new patient" should "persist across restarts" in {
    withHttpRouter[Future](system, config) {
      httpRouter => {
        Get("/api") ~> httpRouter.routes ~> check {
          responseAs[List[Patient]].size shouldBe 0
        }
        Post("/api", Patient(PatientId(0), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart(List.empty))) ~> httpRouter.routes ~> check {
          responseAs[Patient] shouldBe Patient(PatientId(1), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart.emptyChart())
        }
        import scala.collection.JavaConverters._
        git.log().call().asScala.size shouldBe 1
        Get("/api") ~> httpRouter.routes ~> check {
          responseAs[List[Patient]] shouldBe List(Patient(PatientId(1), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart.emptyChart()))
        }
        gitRepo.getState().toOption.get.get(PatientId(1)).get shouldBe Patient(PatientId(1), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart.emptyChart())

      }
    }
  }
}
