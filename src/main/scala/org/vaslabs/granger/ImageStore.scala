package org.vaslabs.granger

import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.IO

object ImageStore {

  def behavior(idGen: IO[UUID]): Behavior[Protocol] = Behaviors.receiveMessage {
    case StoreImage(_, replyTo) =>
      replyTo ! idGen.unsafeRunSync()
      Behaviors.same
  }

  sealed trait Protocol
  case class StoreImage(payload: Array[Byte], replyTo: ActorRef[UUID]) extends Protocol
}
