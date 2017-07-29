package org.vaslabs.granger


import akka.actor.{Actor, Props, Stash}
import akka.pattern._
import akka.util.Timeout
import org.vaslabs.granger.comms.api.model.AddToothInformationRequest
import org.vaslabs.granger.modelv2.Medicament
import scala.concurrent.duration._
/**
  * Created by vnicolaou on 22/07/17.
  */
object RememberInputAgent {
  def props(maxRememberSize: Int): Props =
    Props(new RememberInputAgent(maxRememberSize))

  case class Suggestion(suggestions: Set[String])

  case object Suggest
}

class RememberInputAgent private(maxRememberSize: Int) extends Actor{

  import RememberInputAgent.{Suggest, Suggestion}
  import context.dispatcher

  implicit val timeout = Timeout(1 second)

  val medicamentAgent = context.actorOf(MedicamentAgent.props(maxRememberSize), "MedicamentAgent")

  medicamentAgent ! MedicamentAgent.Init
  
  val rootAgent = context.actorOf(RootAgent.props(maxRememberSize), "RootAgent")

  override def receive(): Receive = {
    case input: AddToothInformationRequest =>
      input.medicament.foreach(medicamentAgent !  _)
      input.roots.foreach(
        roots => roots.foreach(rootAgent ! _)
      )
    case Suggest =>
      (medicamentAgent ? MedicamentAgent.Suggest).mapTo[MedicamentAgent.Suggestion]
            .map(ms => Suggestion(ms.medicamentNames)) pipeTo sender()
  }
}

object MedicamentAgent {
  def props(maxRememberSize: Int): Props = {
    Props(new MedicamentAgent(maxRememberSize))
  }

  case object Init
  case object Suggest
  case class Suggestion(medicamentNames: Set[String])
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
      sender() ! Suggestion(medicaments.map(_.name))
  }

  override def receive: Receive = {
    case Init =>
      loadData()
    case Medicament =>
      stash()
    case Suggest =>
      sender() ! Suggestion(Set.empty)
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
