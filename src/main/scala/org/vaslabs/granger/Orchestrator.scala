package org.vaslabs.granger

import java.io.File
import java.time.Clock

import akka.actor.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import cats.effect.IO
import org.eclipse.jgit.api.Git
import org.vaslabs.granger.PatientManager.{LoadData => LoadPatientData}
import org.vaslabs.granger.RememberInputAgent.MedicamentSuggestions
import org.vaslabs.granger.comms.WebServer
import org.vaslabs.granger.repo.EmptyRepo
import org.vaslabs.granger.repo.git.{EmptyProvider, GitRepo}
import org.vaslabs.granger.system.BaseDirProvider


object Orchestrator {
  sealed trait Protocol
  case class Ping(replyTo: akka.actor.typed.ActorRef[Pong.type]) extends Protocol
  case object Shutdown extends Protocol

  case object Pong


  def behaviour(config: GrangerConfig)(implicit git: Git, clock: Clock, baseDirProvider: BaseDirProvider): Behavior[Protocol] = {
    Behaviors.setup { ctx =>
      import RememberInputAgent.json._

      implicit val actorSystem = ctx.system.toUntyped

      syncRepo().handleErrorWith {
        t => IO(ctx.log.warning(t, "Could not sync with remote database"))
      }.unsafeRunSync()
      val patientManager = ctx.toUntyped.actorOf(PatientManager.props(config), "PatientManager")
      patientManager ! LoadPatientData

      implicit val emptyRememberProvider: EmptyProvider[MedicamentSuggestions] = () => MedicamentSuggestions(List.empty)

      implicit val rememberRepo: GitRepo[MedicamentSuggestions] =
        new GitRepo[MedicamentSuggestions](new File(config.repoLocation), "remember.json")

      val rememberInputAgent: ActorRef =
        ctx.toUntyped.actorOf(RememberInputAgent.props(5), "rememberInputAgent")
      val webServer = new WebServer(patientManager, rememberInputAgent, config)
      webServer.start()
      ctx.log.info("Granger started patient manager")
      initialiseRememberAgent(rememberRepo, rememberInputAgent)
      serverStartedBehaviour(webServer)
    }
  }

  private def serverStartedBehaviour(webServer: WebServer): Behavior[Protocol] = Behaviors.receiveMessage{
    case Shutdown =>
      webServer.shutDown()
      Behaviors.stopped
    case Ping(replyTo) => replyTo ! Pong
      Behavior.same
  }

  private def initialiseRememberAgent(rememberRepo: GitRepo[MedicamentSuggestions], rememberInputAgent: ActorRef) =
    rememberRepo.getState().map(sm => rememberInputAgent ! RememberInputAgent.LoadData(sm))
      .left.foreach(error => {
      error match {
        case EmptyRepo =>
          rememberRepo.saveNew()
          rememberInputAgent ! RememberInputAgent.
            LoadData(MedicamentSuggestions(List.empty))
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
