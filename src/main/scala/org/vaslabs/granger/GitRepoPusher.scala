package org.vaslabs.granger

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.IO
import org.vaslabs.granger.repo.GrangerRepo

import scala.concurrent.duration._

object GitRepoPusher {

  def behavior(grangerRepo: GrangerRepo[_, IO]): Behavior[Protocol] = Behaviors.receive {
    case (ctx, PushChanges) =>
      ctx.scheduleOnce(15 seconds, ctx.self, DoPush)
      pushScheduledBehaviour(grangerRepo)
    case _ => Behaviors.ignore
  }

  private def pushScheduledBehaviour(repo: GrangerRepo[_, IO]): Behavior[Protocol] = Behaviors.receiveMessage {
    case DoPush =>
      repo.pushChanges().unsafeRunSync()
      behavior(repo)
    case PushChanges => Behaviors.ignore
  }

  sealed trait Protocol

  case object PushChanges extends Protocol
  private case object DoPush extends Protocol
}
