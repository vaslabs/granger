package org.vaslabs.granger

import java.time._

import akka.testkit.TestActorRef
import org.scalatest.Matchers
import org.vaslabs.granger.RememberInputAgent.{LoadData, MedicamentStat, MedicamentSuggestions}
import org.vaslabs.granger.comms.api.model.{AddToothInformationRequest}
import org.vaslabs.granger.modelv2.{Medicament, PatientId}

/**
  * Created by vnicolaou on 29/07/17.
  */
class RememberInputSpec extends AkkaBaseSpec("rememberInputTest") with Matchers {

  "remembered medicaments" should "be in the remember list" in {
    val rememberInputAgent = TestActorRef(RememberInputAgent.props(5), "TestRemember")
    rememberInputAgent ! LoadData(MedicamentSuggestions(List.empty))
    rememberInputAgent ! AddToothInformationRequest(
      PatientId(2), 2,
      Some(Medicament("someMed", ZonedDateTime.now(clock))),
      None, None,
      None, None,
      ZonedDateTime.now(clock))

    rememberInputAgent ! RememberInputAgent.Suggest
    expectMsg(MedicamentSuggestions(List(MedicamentStat("someMed", 1))))
  }
}
