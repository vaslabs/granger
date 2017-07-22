package org.vaslabs.granger


import akka.actor.{Actor, Props, Stash}
import akka.pattern._
import org.vaslabs.granger.comms.api.model.AddToothInformationRequest
import org.vaslabs.granger.modelv2.Medicament

/**
  * Created by vnicolaou on 22/07/17.
  */
object RememberInputAgent {
  def props(maxRememberSize: Int): Props =
    Props(new RememberInputAgent(maxRememberSize))

  case class Suggestion(suggestions: Set[String])

  case object GetData
}

class RememberInputAgent private(maxRememberSize: Int) extends Actor{

  import RememberInputAgent.{GetData, Suggestion}
  import context.dispatcher

  val medicamentAgent = context.actorOf(MedicamentAgent.props(maxRememberSize), "MedicamentAgent")

  medicamentAgent ! MedicamentAgent.Init
  
  val rootAgent = context.actorOf(RootAgent.props(maxRememberSize), "RootAgent")

  override def receive(): Receive = {
    case input: AddToothInformationRequest =>
      input.medicament.foreach(medicamentAgent !  _)
      input.roots.foreach(
        roots => roots.foreach(rootAgent ! _)
      )
    case GetData =>
      (medicamentAgent ? MedicamentAgent.Suggest).mapTo[Set[Medicament]]
          .map(suggestions => Suggestion(suggestions.map(_.name))) pipeTo sender()
  }
}

object MedicamentAgent {
  def props(maxRememberSize: Int): Props = {
    Props(new MedicamentAgent(maxRememberSize))
  }

  case object Init
  case object Suggest
  case class Suggestion(medicaments: Set[Medicament])
}

class MedicamentAgent private (maxRememberSize: Int) extends Actor with Stash {

  import MedicamentAgent.{Init, Suggest, Suggestion}

  def loadData(): Unit = {
    context.become(receivePostLoad(Set.empty))
    unstashAll()
  }

  private[this] def receivePostLoad(medicaments: Set[Medicament]): Receive = {
    case m: Medicament =>
      context.become(receivePostLoad((medicaments + m)))
    case Suggest =>
      sender() ! Suggestion(medicaments)
  }

  override def receive: Receive = {
    case Init =>
      loadData()
    case Medicament =>
      stash()
  }
}

object RootAgent {
  def props(maxRememberSize: Int): Props =
    Props(new RootAgent(maxRememberSize))
  case object Init
}

class RootAgent(maxRememberSize: Int) extends Actor {

  def loadData(): Unit = ???

  override def receive: Receive = {
    case RootAgent.Init =>
      loadData()
  }

}
