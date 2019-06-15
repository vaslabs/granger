package org.vaslabs.granger

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.typed.scaladsl.ActorMaterializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.{Assertion, AsyncFlatSpecLike}
import org.vaslabs.granger.comms.{HttpRouter, WebServer}


abstract class HttpBaseSpec extends BaseSpec with AsyncFlatSpecLike with FailFastCirceSupport with ScalatestRouteTest{

  lazy val testKit = ActorTestKit()
  override def beforeAll(): Unit = super.beforeAll()
  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  def withHttpRouter[F[_]](grangerConfig: GrangerConfig)(f: HttpRouter => F[Assertion]): F[Assertion] = {
    implicit val system = testKit.system
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val patientManager = testKit.spawn(PatientManager.behavior(config), "PatientManagerSpec")
    val rememberInputAgent = testKit.spawn(RememberInputAgent.behavior(5), "RememberInputAgent")

    val httpRouter = new WebServer(patientManager, rememberInputAgent, grangerConfig) with HttpRouter
    httpRouter.start()
    f(httpRouter)
  }
}
