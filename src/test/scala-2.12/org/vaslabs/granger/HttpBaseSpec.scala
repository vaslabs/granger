package org.vaslabs.granger

import java.io.{File, FileWriter, PrintWriter}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.{Assertion, AsyncFlatSpecLike, BeforeAndAfter, BeforeAndAfterAll}
import org.vaslabs.granger.PatientManager.LoadData
import org.vaslabs.granger.comms.{HttpRouter, WebServer}
import org.vaslabs.granger.repo.SingleStateGrangerRepo

import scala.concurrent.Await
import scala.concurrent.duration._
/**
  * Created by vnicolaou on 30/07/17.
  */
abstract class HttpBaseSpec extends BaseSpec with AsyncFlatSpecLike with FailFastCirceSupport with ScalatestRouteTest{

  override def beforeAll() = super.beforeAll()
  override def afterAll(): Unit = super.afterAll()

  def withHttpRouter[F[_]](actorSystem: ActorSystem, grangerConfig: GrangerConfig)(f: HttpRouter => F[Assertion]): F[Assertion] = {
    implicit val system = actorSystem
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    val grangerRepo = new SingleStateGrangerRepo()

    val patientManager = actorSystem.actorOf(PatientManager.props(grangerRepo, config))
    patientManager ! LoadData

    val httpRouter = new WebServer(patientManager, grangerConfig) with HttpRouter
    httpRouter.start()
    f(httpRouter)
  }
}
