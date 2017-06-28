package org.vaslabs.granger

import java.time.ZonedDateTime

import org.vaslabs.granger.comms.api.model.AddToothInformationRequest
import org.vaslabs.granger.model._
import org.vaslabs.granger.repo.GitBasedGrangerRepo

/**
  * Created by vnicolaou on 28/06/17.
  */
class HttpRouterAddingInfoToTeethSpec extends BaseSpec{
  import io.circe.generic.auto._
  import io.circe.syntax._
  import io.circe.parser._
  import model.json._
  import GitBasedGrangerRepo._
  "when adding roots to a tooth of a patient it" should "persist" in {
    withHttpRouter(system, config) {
      httpRouter =>
        Post("/api", Patient(PatientId(0), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart(List.empty))) ~> httpRouter.routes ~> check {
          responseAs[Patient] shouldBe Patient(PatientId(1), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart.emptyChart())
        }

        val request = AddToothInformationRequest(PatientId(1), 16, None, None, Some(List(Root(19, "F2", "MB2"))), None).asJson
        Post("/update", AddToothInformationRequest(PatientId(1), 16, None, None, Some(List(Root(19, "F2", "MB2"))), None).asJson) ~> httpRouter.routes ~> check {
          responseAs[Patient].dentalChart.teeth.find(_.number == 16).get.roots shouldBe List(Root(19, "F2", "MB2"))
        }
        import scala.collection.JavaConverters._

        git.log().call().asScala.head.getFullMessage shouldBe "New information for tooth 16 of patient 1"
        Post("/update", AddToothInformationRequest(PatientId(1), 17, Some(Medicament("some medicament", ZonedDateTime.now(clock))), None, Some(List(Root(20, "F2", "MB"))), None)) ~> httpRouter.routes ~> check {
          val tooth = responseAs[Patient].dentalChart.teeth.find(_.number == 17).get
          tooth.medicaments shouldBe List(Medicament("some medicament", ZonedDateTime.now(clock)))
          tooth.roots shouldBe List(Root(20, "F2", "MB"))
        }
        git.log().call().asScala.head.getFullMessage shouldBe "New information for tooth 17 of patient 1"
    }
  }
}
