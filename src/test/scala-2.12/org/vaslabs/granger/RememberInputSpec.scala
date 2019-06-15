package org.vaslabs.granger

import java.time._

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.eventstream.{Publish, Subscribe}
import org.scalatest.{FlatSpecLike, Matchers}
import org.vaslabs.granger.RememberInputAgent.{MedicamentSeen, MedicamentSuggestionAdded, MedicamentSuggestions, RecoverMemory}
import org.vaslabs.granger.comms.api.model.AutocompleteSuggestions
import org.vaslabs.granger.modelv2.Medicament

/**
  * Created by vnicolaou on 29/07/17.
  */
class RememberInputSpec extends AkkaBaseSpec("rememberInputTest")
  with Matchers with BaseSpec with FlatSpecLike {

  val rememberInputEventListener: TestProbe[RememberInputAgent.RememberInputEvent] = testKit.createTestProbe()

  "remembered medicaments" should "be in the remember list" in {
    val rememberInputAgent = testKit.spawn(RememberInputAgent.behavior(5), "TestRemember")

    testKit.system.eventStream ! Subscribe(rememberInputEventListener.ref)

    rememberInputAgent ! RecoverMemory(MedicamentSuggestions(List.empty))

    testKit.system.eventStream ! Publish(MedicamentSeen(Medicament("someMed", ZonedDateTime.now(clock))))

    rememberInputEventListener.expectMessage(MedicamentSuggestionAdded("someMed"))


    val queryActor: TestProbe[AutocompleteSuggestions] = testKit.createTestProbe()
    implicit val sender = queryActor.ref
    rememberInputAgent ! RememberInputAgent.Suggest(sender)

    queryActor.expectMessage(AutocompleteSuggestions(List("someMed")))
  }
}
