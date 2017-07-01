package org.vaslabs.granger

import java.time.ZonedDateTime

import org.vaslabs.granger.PatientManager.{FinishTreatment, StartTreatment}
import org.vaslabs.granger.model.{DentalChart, Patient, PatientId, Treatment}
import org.vaslabs.granger.repo.SingleStateGrangerRepo

/**
  * Created by vnicolaou on 28/06/17.
  */
class HttpRouterAddTreatmentsSpec extends BaseSpec{

  import io.circe.generic.auto._

  import model.json._
  import scala.collection.JavaConverters._
  "only one open treatment" should "exist per tooth" in {
    withHttpRouter(system, config) {
      httpRouter =>
        {
          Post("/api", Patient(PatientId(0), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart(List.empty))) ~> httpRouter.routes ~> check {
            responseAs[Patient] shouldBe Patient(PatientId(1), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart.emptyChart())
          }
          Post("/treatment/start", StartTreatment(PatientId(1), 11, "A treatment")) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), None, "A treatment"))
          }
          git.log().call().asScala.head.getFullMessage shouldBe "Started treatment for tooth 11 on patient 1"
          Post("/treatment/start", StartTreatment(PatientId(1), 11, "B treatment")) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), None, "A treatment"))
          }
          git.log().call().asScala.head.getFullMessage shouldBe "Started treatment for tooth 11 on patient 1"
          Post("/treatment/finish", FinishTreatment(PatientId(1), 11)) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), Some(ZonedDateTime.now(clock)), "A treatment"))
          }
          git.log().call().asScala.head.getFullMessage shouldBe "Finished treatment for tooth 11 on patient 1"
          Post("/treatment/start", StartTreatment(PatientId(1), 11, "B treatment")) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), None, "B treatment"), Treatment(ZonedDateTime.now(clock), Some(ZonedDateTime.now(clock)), "A treatment"))
          }
          git.log().call().asScala.head.getFullMessage shouldBe "Started treatment for tooth 11 on patient 1"
          gitRepo.getState().toOption.flatMap(_.get(PatientId(1))).get.dentalChart.teeth
            .find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), None, "B treatment"), Treatment(ZonedDateTime.now(clock), Some(ZonedDateTime.now(clock)), "A treatment"))
        }
    }
  }

}
