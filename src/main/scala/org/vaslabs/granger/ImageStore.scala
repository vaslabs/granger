package org.vaslabs.granger

import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.IO
import org.vaslabs.granger.modelv2.{PatientId, PatientImages}

object ImageStore {

  def behavior(idGen: IO[UUID]): Behavior[Protocol] = Behaviors.receiveMessage {
    case StoreImage(_, _, replyTo) =>
      replyTo ! idGen.unsafeRunSync()
      Behaviors.same
    case FetchImages(patientId, replyTo) =>
      replyTo ! PatientImages(patientId, List(idGen.unsafeRunSync()))
      Behaviors.same
  }

  sealed trait Protocol
  case class StoreImage(patientId: PatientId, payload: Array[Byte], replyTo: ActorRef[UUID]) extends Protocol

  case class FetchImages(patientId: modelv2.PatientId, replyTo: ActorRef[modelv2.PatientImages]) extends Protocol

}
