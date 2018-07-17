package org.vaslabs.granger

import java.time._

import akka.testkit.TestActorRef
import org.scalatest.{FlatSpecLike, Matchers}
import org.vaslabs.granger.RememberInputAgent.{LoadData, MedicamentStat, MedicamentSuggestionAdded, MedicamentSuggestions}
import org.vaslabs.granger.comms.api.model.{AddToothInformationRequest, AutocompleteSuggestions}
import org.vaslabs.granger.modelv2.{Medicament, PatientId}

/**
  * Created by vnicolaou on 29/07/17.
  */
class RememberInputSpec extends AkkaBaseSpec("rememberInputTest") with Matchers with BaseSpec with FlatSpecLike{

  "remembered medicaments" should "be in the remember list" in {
    val rememberInputAgent = TestActorRef(RememberInputAgent.props(5), "TestRemember")
    rememberInputAgent ! LoadData(MedicamentSuggestions(List.empty))
    system.eventStream.subscribe(self, classOf[RememberInputAgent.RememberInputEvent])

    val addToothInformationRequest = AddToothInformationRequest(
      PatientId(2), 2,
      Some(Medicament("someMed", ZonedDateTime.now(clock))),
      None, None,
      None, None,
      ZonedDateTime.now(clock))
    system.eventStream.publish(addToothInformationRequest)

    expectMsg(MedicamentSuggestionAdded("someMed"))

    rememberInputAgent ! RememberInputAgent.Suggest
    expectMsg(AutocompleteSuggestions(List("someMed")))
  }
}
