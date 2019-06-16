package org.vaslabs.granger

import java.util.UUID

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.Id
import org.scalatest.Matchers

import scala.util.Random

class HttpRouterAddPatientImageSpec extends HttpBaseSpec with ScalatestRouteTest with Matchers {

  val jpegImage = Random.nextString(256).getBytes("UTF-8")


  "images per patient" can "be accepted" in {

    implicit val expectedImageKey = UUID.randomUUID().toString

    withHttpRouter[Id](config) { router =>
      val patientImageForm = Multipart.FormData(
        Multipart.FormData.BodyPart.Strict(
          "jpeg",
          HttpEntity(ContentType(MediaTypes.`image/jpeg`), jpegImage)
        )
      )

      Put("/patient/1/images", patientImageForm) ~> router.routes ~> check {
        response.status shouldBe StatusCodes.Accepted
      }
    }


  }

}
