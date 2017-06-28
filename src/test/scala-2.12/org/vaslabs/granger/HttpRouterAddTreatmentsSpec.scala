package org.vaslabs.granger

import java.time.ZonedDateTime

import org.vaslabs.granger.PatientManager.{FinishTreatment, StartTreatment}
import org.vaslabs.granger.model.{DentalChart, Patient, PatientId, Treatment}
import org.vaslabs.granger.repo.GitBasedGrangerRepo

/**
  * Created by vnicolaou on 28/06/17.
  */
class HttpRouterAddTreatmentsSpec extends BaseSpec{

  import io.circe.generic.auto._

  import model.json._
  import GitBasedGrangerRepo._
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
          Post("/treatment/start", StartTreatment(PatientId(1), 11, "B treatment")) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), None, "A treatment"))
          }
          Post("/treatment/finish", FinishTreatment(PatientId(1), 11)) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), Some(ZonedDateTime.now(clock)), "A treatment"))
          }
          Post("/treatment/start", StartTreatment(PatientId(1), 11, "B treatment")) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe List(Treatment(ZonedDateTime.now(clock), None, "B treatment"), Treatment(ZonedDateTime.now(clock), Some(ZonedDateTime.now(clock)), "A treatment"))
          }
        }
    }
  }

}
