package org.vaslabs.granger

import java.time.ZonedDateTime

import cats.Id
import org.scalatest.Matchers
import org.vaslabs.granger.modeltreatments.RootCanalTreatment
import org.vaslabs.granger.modelv2.{Patient, PatientId}
import org.vaslabs.granger.v2json._
import io.circe.generic.auto._
import org.vaslabs.granger.comms.UserApi
/**
  * Created by vnicolaou on 10/09/17.
  */
class HttpRouterDeletingTreatmentsSpec extends HttpBaseSpec with Matchers{

  "when adding a treatment we" should "be able to delete it" in {
    withHttpRouter[Id](config) {
      httpRouter => {

        Post("/api", withNewPatient()) ~> httpRouter.routes ~> check {
          responseAs[Patient] shouldBe withPatient(PatientId(1))
        }

        Post("/treatment/start", UserApi.StartTreatment(PatientId(1), 11, RootCanalTreatment())) ~>
              httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 11).get.treatments shouldBe
            List(withOpenTreatment())
        }

        Post("/treatment/delete", UserApi.DeleteTreatment(PatientId(1), 11, ZonedDateTime.now(clock))) ~>
          httpRouter.routes ~> check {
          responseAs[Patient] shouldBe withPatient(PatientId(1))
        }

      }
    }
  }

}
