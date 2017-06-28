package org.vaslabs.granger

import java.io.{File, FileWriter, PrintWriter}
import java.time.{Clock, Instant, ZoneOffset, ZonedDateTime}

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.scalatest._
import org.vaslabs.granger.comms.{GrangerApi, HttpRouter, WebServer}
import org.vaslabs.granger.repo.GitBasedGrangerRepo
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server._
import Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.{FailFastCirceSupport, FailFastUnmarshaller}
import org.vaslabs.granger.model.{DentalChart, Patient, PatientId}
import pureconfig._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Success


/**
  * Created by vnicolaou on 28/06/17.
  */
class HttpRouterAddingPatientsSpec extends BaseSpec {
  import io.circe.generic.auto._

  import model.json._
  import GitBasedGrangerRepo._
  "adding a new patient" should "persist across restarts" in {
    withHttpRouter[Future](system, config) {
      httpRouter => {
        Get("/api") ~> httpRouter.routes ~> check {
          responseAs[List[Patient]].size shouldBe 0
        }
        Post("/api", Patient(PatientId(0), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart(List.empty))) ~> httpRouter.routes ~> check {
          responseAs[Patient] shouldBe Patient(PatientId(1), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart.emptyChart())
        }
        import scala.collection.JavaConverters._
        git.log().call().asScala.size shouldBe 1
        Get("/api") ~> httpRouter.routes ~> check {
          responseAs[List[Patient]] shouldBe List(Patient(PatientId(1), "FirstName", "LastName", ZonedDateTime.now(clock).toLocalDate, DentalChart.emptyChart()))
        }
      }
    }
  }
}
