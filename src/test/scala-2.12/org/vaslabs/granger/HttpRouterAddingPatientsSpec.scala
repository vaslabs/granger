package org.vaslabs.granger


import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.Id
import org.vaslabs.granger.modelv2.{Patient, PatientId}
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
        Post("/api", withNewPatient()) ~> httpRouter.routes ~> check {
          responseAs[Patient] shouldBe withPatient(PatientId(1))
        }
        import scala.collection.JavaConverters._
        git.log().call().asScala.size shouldBe 2
        Get("/api") ~> httpRouter.routes ~> check {
          responseAs[List[Patient]] shouldBe List(withPatient(PatientId(1)))
        }
        gitRepo.getState().toOption.get.get(PatientId(1)).get shouldBe withPatient(PatientId(1))
      }
    }
  }
}
