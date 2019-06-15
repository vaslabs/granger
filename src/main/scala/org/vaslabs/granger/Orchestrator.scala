package org.vaslabs.granger

import java.io.File
import java.time.Clock

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.IO
import org.eclipse.jgit.api.Git
import org.vaslabs.granger.RememberInputAgent.MedicamentSuggestions
import org.vaslabs.granger.comms.WebServer
import org.vaslabs.granger.repo.EmptyRepo
import org.vaslabs.granger.repo.git.{ EmptyProvider, GitRepo }
import org.vaslabs.granger.system.BaseDirProvider

object Orchestrator {
  sealed trait Protocol
  case class Ping(replyTo: akka.actor.typed.ActorRef[Pong.type]) extends Protocol
  case object Shutdown extends Protocol

  case object Pong

  def behaviour(
      config: GrangerConfig)(implicit git: Git, clock: Clock, baseDirProvider: BaseDirProvider): Behavior[Protocol] = {
    Behaviors.setup { ctx =>
      import RememberInputAgent.json._

      implicit val actorSystem = ctx.system

      syncRepo()
        .handleErrorWith { t =>
          IO(ctx.log.warning(t, "Could not sync with remote database"))
        }
        .unsafeRunSync()
      val patientManager = ctx.spawn(PatientManager.behavior(config), "PatientManager")

      implicit val emptyRememberProvider: EmptyProvider[MedicamentSuggestions] = () => MedicamentSuggestions(List.empty)

      implicit val rememberRepo: GitRepo[MedicamentSuggestions] =
        new GitRepo[MedicamentSuggestions](new File(config.repoLocation), "remember.json")

      val rememberInputAgent: ActorRef[RememberInputAgent.Protocol] =
        ctx.spawn(RememberInputAgent.behavior(5), "rememberInputAgent")
      val webServer = new WebServer(patientManager, rememberInputAgent, config)
      webServer.start()
      ctx.log.info("Granger started patient manager")
      initialiseRememberAgent(rememberRepo, rememberInputAgent)
      serverStartedBehaviour(webServer)
    }
  }

  private def serverStartedBehaviour(webServer: WebServer): Behavior[Protocol] = Behaviors.receiveMessage {
    case Shutdown =>
      webServer.shutDown()
      Behaviors.stopped
    case Ping(replyTo) =>
      replyTo ! Pong
      Behaviors.same
  }

  private def initialiseRememberAgent(
      rememberRepo: GitRepo[MedicamentSuggestions],
      rememberInputAgent: ActorRef[RememberInputAgent.Protocol]) =
    rememberRepo
      .getState()
      .map(sm => rememberInputAgent ! RememberInputAgent.RecoverMemory(sm))
      .left
      .foreach(error => {
        error match {
          case EmptyRepo =>
            rememberRepo.saveNew()
            rememberInputAgent ! RememberInputAgent.RecoverMemory(MedicamentSuggestions(List.empty))
        }
      })

  private def syncRepo()(implicit git: Git) =
    IO {
      val pullCommand = git.pull()
      pullCommand.setRemote("origin")
      pullCommand.setRemoteBranchName("master")
      pullCommand.call()
    }
}
