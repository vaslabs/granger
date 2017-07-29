package org.vaslabs.granger

import java.time._

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.FlatSpecLike
import org.vaslabs.granger.RememberInputAgent.Suggestion
import org.vaslabs.granger.comms.api.model.{AddToothInformationRequest}
import org.vaslabs.granger.modelv2.{Medicament, PatientId}

/**
  * Created by vnicolaou on 29/07/17.
  */
class RememberInputSpec extends TestKit(ActorSystem("rememberInputTest")) with FlatSpecLike with ImplicitSender {

  implicit val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

  "remembered medicaments" should "be in the remember list" in {
    val rememberInputAgent = system.actorOf(RememberInputAgent.props(5))
    rememberInputAgent ! AddToothInformationRequest(
      PatientId(2), 2,
      Some(Medicament("someMed", ZonedDateTime.now(clock))),
      None, None,
      None, None,
      ZonedDateTime.now(clock))

    rememberInputAgent ! RememberInputAgent.Suggest
    expectMsg(Suggestion(Set("someMed")))
  }
}
