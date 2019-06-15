package org.vaslabs.granger

import java.io.IOException

import akka.actor.typed.{ Behavior, SupervisorStrategy }
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.IO
import org.vaslabs.granger.repo.GrangerRepo

import scala.concurrent.duration._

object GitRepoPusher {

  def behavior(grangerRepo: GrangerRepo[_, IO]): Behavior[Protocol] =
    Behaviors
      .supervise(unsafeBehavior(grangerRepo))
      .onFailure[IOException](SupervisorStrategy.restartWithBackoff(10 seconds, 5 minutes, 0.2).withMaxRestarts(10))

  private def unsafeBehavior(grangerRepo: GrangerRepo[_, IO]): Behavior[Protocol] = Behaviors.receive {
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
