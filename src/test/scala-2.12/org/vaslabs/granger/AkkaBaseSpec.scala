package org.vaslabs.granger

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.adapter._
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration.FiniteDuration

/**
  * Created by vnicolaou on 30/07/17.
  */
abstract class AkkaBaseSpec(name: String) extends BaseSpec with BeforeAndAfterAll {

  val testKit = ActorTestKit()
  implicit val system = testKit.system.toUntyped


  val implicitSenderProbe = testKit.createTestProbe[Any]("ImplicitSender")
  implicit val sender = implicitSenderProbe.ref

  override def afterAll() = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  override def beforeAll() = {
    super.beforeAll()
  }

  def expectMsg[A](msg: A) = implicitSenderProbe.expectMessage(msg)

  def expectNoMessage(duration: FiniteDuration) = implicitSenderProbe.expectNoMessage(duration)
}
