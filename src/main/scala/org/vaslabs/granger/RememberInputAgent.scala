package org.vaslabs.granger


import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import org.vaslabs.granger.RememberInputAgent.{MedicamentStat, MedicamentSuggestions}
import org.vaslabs.granger.comms.api.model.{AddToothInformationRequest, AutocompleteSuggestions}
import org.vaslabs.granger.modelv2.Medicament
import org.vaslabs.granger.repo.Repo

import scala.concurrent.duration._
/**
  * Created by vnicolaou on 22/07/17.
  */
object RememberInputAgent {
  def props(maxRememberSize: Int)(implicit repo: Repo[RememberInputAgent.MedicamentSuggestions]): Props =
    Props(new RememberInputAgent(maxRememberSize))

  case class Suggestion(suggestions: Set[String])

  case object Suggest
  case class LoadData(medicamentSuggestions: MedicamentSuggestions)

  case class MedicamentSuggestions(medicamentsUsed: List[MedicamentStat])
  case class MedicamentStat(medicamentName: String, usageCounter: Int)
}

class RememberInputAgent private(maxRememberSize: Int) extends Actor with ActorLogging{

  import RememberInputAgent.{Suggest, LoadData}
  import context.dispatcher

  implicit val timeout = Timeout(1 second)

  def recordSuggestion(medicament: Medicament, state: RememberInputAgent.MedicamentSuggestions): RememberInputAgent.MedicamentSuggestions = {
    val medicamentStat = state.medicamentsUsed.find(medicament.name == _.medicamentName)
        .map(m => m.copy(usageCounter = m.usageCounter + 1))
        .getOrElse(MedicamentStat(medicament.name, 1))
    val newList = MedicamentSuggestions(medicamentStat :: state.medicamentsUsed.filterNot(_.medicamentName == medicament.name))
    newList
  }


  override def receive: Receive = {
    case LoadData(medicamentSuggestions) =>
        context.become(receivePostLoad(medicamentSuggestions))
    case Suggest =>
      sender() ! AutocompleteSuggestions(List.empty)
  }

  def receivePostLoad(suggestionsState: RememberInputAgent.MedicamentSuggestions): Receive = {
    case input: AddToothInformationRequest =>
      val senderRef = sender()
      log.debug(s"Received request to remember things ${input}")
      input.medicament.map(m => {
        val newState = recordSuggestion(m, suggestionsState)
        senderRef ! newState
        context.become(receivePostLoad(newState))
      })
    case Suggest =>
      sender() ! AutocompleteSuggestions(suggestionsState.medicamentsUsed.map(_.medicamentName))
  }
}