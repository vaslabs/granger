package org.vaslabs.granger

import java.util.concurrent.Executors

import akka.actor.typed.eventstream.{ Publish, Subscribe }
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.{ Decoder, Encoder }
import org.vaslabs.granger.comms.api.model.AutocompleteSuggestions
import org.vaslabs.granger.modelv2.Medicament
import org.vaslabs.granger.repo.Repo

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Created by vnicolaou on 22/07/17.
 */
object RememberInputAgent {

  def behavior(maxRememberSize: Int)(implicit repo: Repo[MedicamentSuggestions]): Behavior[Protocol] =
    Behaviors.setup[Protocol] { ctx =>
      ctx.system.eventStream ! Subscribe[MedicamentSeen](ctx.self)
      Behaviors.receiveMessage {
        case RecoverMemory(suggestions) =>
          behaviorWithDataAndSaving(maxRememberSize, suggestions)
        case Suggest(replyTo) =>
          replyTo ! AutocompleteSuggestions(List.empty)
          Behaviors.same
        case MedicamentSeen(medicament: Medicament) =>
          behaviorWithData(maxRememberSize, MedicamentSuggestions(List(MedicamentStat(medicament.name, 1))))
      }
    }

  private def behaviorWithData(maxRememberSize: Int, medicamentSuggestions: MedicamentSuggestions)(
      implicit repo: Repo[MedicamentSuggestions]): Behavior[Protocol] =
    Behaviors.receiveMessage {
      case RecoverMemory(suggestions) =>
        val allSuggestions =
          (suggestions.medicamentsUsed ++ medicamentSuggestions.medicamentsUsed)
            .groupBy(_.medicamentName)
            .mapValues(_.map(_.usageCounter).sum)
            .map {
              case (name, usage) => MedicamentStat(name, usage)
            }
        val sorted = allSuggestions.toList.sortBy(_.usageCounter)
        val newMedicamentSuggestions = MedicamentSuggestions(sorted.drop(sorted.size - maxRememberSize))

        repo.save("Recovered memory and saving everything available", newMedicamentSuggestions)

        behaviorWithDataAndSaving(maxRememberSize, newMedicamentSuggestions)
      case MedicamentSeen(medicament: Medicament) =>
        behaviorWithData(maxRememberSize, recordSuggestion(medicament, medicamentSuggestions))
      case Suggest(replyTo) =>
        replyTo !
        AutocompleteSuggestions(medicamentSuggestions.medicamentsUsed.map(_.medicamentName))
        Behaviors.same
    }

  private def behaviorWithDataAndSaving(maxRememberSize: Int, medicamentSuggestions: MedicamentSuggestions)(
      implicit repo: Repo[MedicamentSuggestions]): Behavior[Protocol] =
    Behaviors.receive {
      case (ctx, MedicamentSeen(medicament: Medicament)) =>
        val newMedicamentSuggestions = recordSuggestion(medicament, medicamentSuggestions)
        repo.save(s"Saving medicament for suggestions ${medicament.name}", newMedicamentSuggestions)
        ctx.system.eventStream ! Publish(MedicamentSuggestionAdded(medicament.name))

        behaviorWithDataAndSaving(maxRememberSize, recordSuggestion(medicament, medicamentSuggestions))

      case (_, Suggest(replyTo)) =>
        replyTo !
        AutocompleteSuggestions(medicamentSuggestions.medicamentsUsed.map(_.medicamentName))
        Behaviors.same
      case (_, recoverMemory: RecoverMemory) => Behaviors.ignore
    }

  private[granger] def recordSuggestion(medicament: Medicament, state: MedicamentSuggestions): MedicamentSuggestions = {
    val medicamentStat = state.medicamentsUsed
      .find(medicament.name == _.medicamentName)
      .map(m => m.copy(usageCounter = m.usageCounter + 1))
      .getOrElse(MedicamentStat(medicament.name, 1))
    val newList = MedicamentSuggestions(
      medicamentStat :: state.medicamentsUsed.filterNot(_.medicamentName == medicament.name))
    newList
  }

  sealed trait Protocol

  case class Suggestion(suggestions: Set[String])

  case class Suggest(replyTo: ActorRef[AutocompleteSuggestions]) extends Protocol

  case class MedicamentSeen(medicament: Medicament) extends Protocol

  case class MedicamentSuggestions(medicamentsUsed: List[MedicamentStat])

  case class MedicamentStat(medicamentName: String, usageCounter: Int)

  sealed trait RememberInputEvent

  case class RecoverMemory(medicamentSuggestions: MedicamentSuggestions) extends Protocol

  case class MedicamentSuggestionAdded(medicamentName: String) extends RememberInputEvent

  object json {

    implicit val jsonMedStatEncoder: Encoder[MedicamentStat] = deriveEncoder[MedicamentStat]
    implicit val jsonMedStatDecoder: Decoder[MedicamentStat] = deriveDecoder[MedicamentStat]
    implicit val jsonSuggestionsEncoder: Encoder[MedicamentSuggestions] = deriveEncoder[MedicamentSuggestions]
    implicit val jsonSuggestionsDecoder: Decoder[MedicamentSuggestions] = deriveDecoder[MedicamentSuggestions]
  }

}
