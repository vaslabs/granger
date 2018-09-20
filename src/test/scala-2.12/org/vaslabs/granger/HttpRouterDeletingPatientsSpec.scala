package org.vaslabs.granger

import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.Id
import org.scalatest.Matchers
import org.vaslabs.granger.PatientManager.{CommandOutcome, Failure, Success}
import org.vaslabs.granger.modelv2.{Patient, PatientId}
import org.vaslabs.granger.v2json._

/**
  * Created by vnicolaou on 28/06/17.
  */
class HttpRouterDeletingPatientsSpec extends HttpBaseSpec with ScalatestRouteTest with Matchers{

  import io.circe.generic.auto._
  "deleting a patient" should "persist across restarts" in {
    withHttpRouter[Id](system, config) {
      httpRouter => {
        import scala.collection.JavaConverters._
        val initialSize = git.log().call().asScala.size
        Get("/api") ~> httpRouter.routes ~> check {
          responseAs[List[Patient]].size shouldBe 0
        }
        Post("/api", withNewPatient()) ~> httpRouter.routes ~> check {
          responseAs[Patient] shouldBe withPatient(PatientId(1))
        }

        git.log().call().asScala.size shouldBe initialSize + 1

        Delete("/patient/2") ~> httpRouter.routes ~> check {
          responseAs[CommandOutcome] should matchPattern {
            case Failure(_) =>
          }
        }

        Delete("/patient/1") ~> httpRouter.routes ~> check {
          responseAs[CommandOutcome] shouldBe Success
        }

        gitRepo.getState().toOption.get.get(PatientId(1)) shouldBe None


      }
    }
  }
}
