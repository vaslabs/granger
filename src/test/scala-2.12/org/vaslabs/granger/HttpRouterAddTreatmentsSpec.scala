package org.vaslabs.granger

import java.time.{Clock, Instant, ZoneOffset, ZonedDateTime}

import cats.Id
import org.vaslabs.granger.PatientManager.{Failure, FinishTreatment}
import org.vaslabs.granger.modeltreatments._
import org.vaslabs.granger.modelv2._
import org.vaslabs.granger.v2json._
import io.circe.generic.auto._
import org.scalatest.Matchers
import org.vaslabs.granger.repo.ToothHasActiveTreatment

import scala.collection.JavaConverters._

/**
  * Created by vnicolaou on 28/06/17.
  */
class HttpRouterAddTreatmentsSpec extends HttpBaseSpec with Matchers{


  "only one open treatment" should "exist per tooth" in {
    withHttpRouter[Id](system, config) {
      httpRouter =>
        {
          import io.circe.generic.auto._
          Post("/api", withNewPatient()) ~> httpRouter.routes ~> check {
            responseAs[Patient] shouldBe withPatient(PatientId(1))
          }
          Post("/treatment/start", withStartTreatment(PatientId(1), 11)) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe
              List(Treatment(ZonedDateTime.now(clock), None, RootCanalTreatment()))
          }

          git.log().call().asScala.head.getFullMessage shouldBe
            "Started treatment for tooth 11 on patient 1"

          Post("/treatment/start", withStartTreatment(PatientId(1), 11)) ~> httpRouter.routes ~> check {
            responseAs[Failure] shouldBe
              Failure(ToothHasActiveTreatment(PatientId(1), 11).error)
          }

          git.log().call().asScala.head.getFullMessage shouldBe
            "Started treatment for tooth 11 on patient 1"

          val finishAt = ZonedDateTime.now(clock).plusHours(2)
          Post("/treatment/finish", FinishTreatment(PatientId(1), 11, finishAt)) ~> httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe
              List(
                withCompletedTreatment(finishAt = finishAt)
              )
          }
          git.log().call().asScala.head.getFullMessage shouldBe
            "Finished treatment for tooth 11 on patient 1"

          Post("/treatment/start", withStartTreatment(PatientId(1), 11, RepeatRootCanalTreatment())) ~>
            httpRouter.routes ~> check {
            responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe
              List(
                withOpenTreatment(RepeatRootCanalTreatment()),
                withCompletedTreatment(finishAt = finishAt)
              )
          }
          git.log().call().asScala.head.getFullMessage shouldBe
            "Started treatment for tooth 11 on patient 1"

          gitRepo.getState().toOption.flatMap(_.get(PatientId(1))).get.dentalChart.teeth
            .find(_.number == 11).get.treatments shouldBe
            List(
              withOpenTreatment(RepeatRootCanalTreatment()),
              withCompletedTreatment(finishAt = finishAt)
            )
        }
    }
  }
}
