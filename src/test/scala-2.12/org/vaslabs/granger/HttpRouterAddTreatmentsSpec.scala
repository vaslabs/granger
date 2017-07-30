package org.vaslabs.granger

import java.time.ZonedDateTime

import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.Id
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.vaslabs.granger.PatientManager.{FinishTreatment, StartTreatment}
import org.vaslabs.granger.modeltreatments._
import org.vaslabs.granger.modelv2._
import org.vaslabs.granger.v2json._
import io.circe.generic.auto._
import org.scalatest.Matchers
/**
  * Created by vnicolaou on 28/06/17.
  */
class HttpRouterAddTreatmentsSpec extends HttpBaseSpec with ScalatestRouteTest with Matchers{


  import scala.collection.JavaConverters._

  def withNewPatient(
        firstName: String = "FirstName",
        lastName: String = "LastName",
        dentalChart: DentalChart = DentalChart(List.empty)): Patient =
    Patient(
      PatientId(0), firstName, lastName,
      ZonedDateTime.now(clock).toLocalDate, dentalChart
    )

  def withPatient(patientId: PatientId,
                  firstName: String = "FirstName",
                  lastName: String = "LastName",
                  dentalChart: DentalChart = DentalChart(List.empty)
                 ): Patient =
    Patient(
      patientId, firstName, lastName,
      ZonedDateTime.now(clock).toLocalDate, dentalChart
    )

  def withStartTreatment(
       patientId: PatientId,
       toothNumber: Int,
       treatment: TreatmentCategory = RootCanalTreatment()) =
    StartTreatment(patientId, toothNumber, treatment)

  def withCompletedTreatment(treatmentCategory: TreatmentCategory = RootCanalTreatment()): Treatment =
    Treatment(ZonedDateTime.now(clock),
    Some(ZonedDateTime.now(clock)),
      treatmentCategory)

  def withOpenTreatment(treatmentCategory: TreatmentCategory = RootCanalTreatment()): Treatment =
    Treatment(ZonedDateTime.now(clock),
      None,
      treatmentCategory)

  "only one open treatment" should "exist per tooth" in {
    withHttpRouter[Id](system, config) {
      httpRouter =>
        {
          import io.circe.generic.auto._
          Post("/api", withNewPatient()) ~> httpRouter.routes ~> check {
            responseAs[Patient] shouldBe withPatient(PatientId(1), dentalChart = DentalChart.emptyChart())
          }
          Post("/treatment/start", withStartTreatment(PatientId(1), 11)) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe
              List(Treatment(ZonedDateTime.now(clock), None, RootCanalTreatment()))
          }

          git.log().call().asScala.head.getFullMessage shouldBe
            "Started treatment for tooth 11 on patient 1"

          Post("/treatment/start", withStartTreatment(PatientId(1), 11)) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe
              List(withOpenTreatment())
          }

          git.log().call().asScala.head.getFullMessage shouldBe
            "Started treatment for tooth 11 on patient 1"

          Post("/treatment/finish", FinishTreatment(PatientId(1), 11)) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe
              List(
                withCompletedTreatment()
              )
          }
          git.log().call().asScala.head.getFullMessage shouldBe
            "Finished treatment for tooth 11 on patient 1"

          Post("/treatment/start", withStartTreatment(PatientId(1), 11, RepeatRootCanalTreatment())) ~>
            httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe
              List(
                withOpenTreatment(RepeatRootCanalTreatment()),
                withCompletedTreatment()
              )
          }
          git.log().call().asScala.head.getFullMessage shouldBe
            "Started treatment for tooth 11 on patient 1"

          gitRepo.getState().toOption.flatMap(_.get(PatientId(1))).get.dentalChart.teeth
            .find(_.number == 11).get.treatments shouldBe
            List(
              withOpenTreatment(RepeatRootCanalTreatment()),
              withCompletedTreatment()
            )
        }
    }
  }
}
