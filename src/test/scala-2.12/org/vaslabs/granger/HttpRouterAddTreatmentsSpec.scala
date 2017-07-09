package org.vaslabs.granger

import java.time.ZonedDateTime

import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.vaslabs.granger.PatientManager.{FinishTreatment, StartTreatment}
import org.vaslabs.granger.modeltreatments._
import org.vaslabs.granger.modelv2._
import org.vaslabs.granger.v2json._
import io.circe.generic.auto._
/**
  * Created by vnicolaou on 28/06/17.
  */
class HttpRouterAddTreatmentsSpec extends BaseSpec with FailFastCirceSupport{


  import scala.collection.JavaConverters._
  "only one open treatment" should "exist per tooth" in {
    withHttpRouter(system, config) {
      httpRouter =>
        {
          import io.circe.generic.auto._
          Post("/api", Patient(PatientId(0), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart(List.empty))) ~> httpRouter.routes ~> check {
            responseAs[Patient] shouldBe Patient(PatientId(1), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart.emptyChart())
          }
          Post("/treatment/start", StartTreatment(PatientId(1), 11, RootCanalTreatment())) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), None, RootCanalTreatment()))
          }
          git.log().call().asScala.head.getFullMessage shouldBe "Started treatment for tooth 11 on patient 1"
          Post("/treatment/start", StartTreatment(PatientId(1), 11, RootCanalTreatment())) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), None, RootCanalTreatment()))
          }
          git.log().call().asScala.head.getFullMessage shouldBe "Started treatment for tooth 11 on patient 1"
          Post("/treatment/finish", FinishTreatment(PatientId(1), 11)) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), Some(ZonedDateTime.now(clock)), RootCanalTreatment()))
          }
          git.log().call().asScala.head.getFullMessage shouldBe "Finished treatment for tooth 11 on patient 1"
          Post("/treatment/start", StartTreatment(PatientId(1), 11, RepeatRootCanalTreatment())) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), None, RepeatRootCanalTreatment()), Treatment(ZonedDateTime.now(clock), Some(ZonedDateTime.now(clock)), RootCanalTreatment()))
          }
          git.log().call().asScala.head.getFullMessage shouldBe "Started treatment for tooth 11 on patient 1"
          gitRepo.getState().toOption.flatMap(_.get(PatientId(1))).get.dentalChart.teeth
            .find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), None, RepeatRootCanalTreatment()), Treatment(ZonedDateTime.now(clock), Some(ZonedDateTime.now(clock)), RootCanalTreatment()))
        }
    }
  }
}
