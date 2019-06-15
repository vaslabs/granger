package org.vaslabs.granger

import java.time._

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.eventstream.Subscribe
import akka.testkit.TestActorRef
import org.scalatest.{FlatSpecLike, Matchers}
import org.vaslabs.granger.RememberInputAgent.{LoadData, MedicamentSuggestionAdded, MedicamentSuggestions}
import org.vaslabs.granger.comms.api.model.{AddToothInformationRequest, AutocompleteSuggestions}
import org.vaslabs.granger.modelv2.{Medicament, PatientId}

/**
  * Created by vnicolaou on 29/07/17.
  */
class RememberInputSpec extends AkkaBaseSpec("rememberInputTest")
  with Matchers with BaseSpec with FlatSpecLike {

  val rememberInputEventListener: TestProbe[RememberInputAgent.RememberInputEvent] = testKit.createTestProbe()
  "remembered medicaments" should "be in the remember list" in {
    val rememberInputAgent = TestActorRef(RememberInputAgent.props(5), "TestRemember")
    rememberInputAgent ! LoadData(MedicamentSuggestions(List.empty))
    testKit.system.eventStream ! Subscribe(rememberInputEventListener.ref)

    val addToothInformationRequest = AddToothInformationRequest(
      PatientId(2), 2,
      Some(Medicament("someMed", ZonedDateTime.now(clock))),
      None, None,
      None, None,
      ZonedDateTime.now(clock))
    system.eventStream.publish(addToothInformationRequest)

    rememberInputEventListener.expectMessage(MedicamentSuggestionAdded("someMed"))


    val queryActor: TestProbe[AutocompleteSuggestions] = testKit.createTestProbe()
    implicit val sender = queryActor.ref.toUntyped
    rememberInputAgent ! RememberInputAgent.Suggest

    queryActor.expectMessage(AutocompleteSuggestions(List("someMed")))
  }
}
