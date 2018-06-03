package org.vaslabs.granger

import java.io.File
import java.time.Clock

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.stream.ActorMaterializer
import org.eclipse.jgit.api.Git
import org.vaslabs.granger.PatientManager.{LoadData => LoadPatientData}
import org.vaslabs.granger.comms.WebServer
import org.vaslabs.granger.system.BaseDirProvider
import cats.syntax.either._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.vaslabs.granger.RememberInputAgent.MedicamentSuggestions
import org.vaslabs.granger.repo.EmptyRepo
import org.vaslabs.granger.repo.git.{EmptyProvider, GitRepo}

/**
  * Created by vnicolaou on 28/08/17.
  */
class Orchestrator private (config: GrangerConfig)
                           (implicit git: Git, clock: Clock, baseDirProvider: BaseDirProvider)
  extends Actor with ActorLogging with Stash
{

  import Orchestrator._

  val patientManager = context.actorOf(PatientManager.props(config), "patientManager")

  implicit val emptyRememberProvider: EmptyProvider[MedicamentSuggestions] = () => MedicamentSuggestions(List.empty)

  import RememberInputAgent.json._
  implicit val rememberRepo: GitRepo[MedicamentSuggestions] =
    new GitRepo[MedicamentSuggestions](new File(config.repoLocation), "remember.json")

  val rememberInputAgent: ActorRef =
    context.actorOf(RememberInputAgent.props(5), "rememberInputAgent")


  def initialiseRememberAgent() =
    rememberRepo.getState().map(sm => rememberInputAgent ! RememberInputAgent.LoadData(sm))
      .left.foreach(error => {
      error match {
        case EmptyRepo =>
          rememberRepo.saveNew()
          rememberInputAgent ! RememberInputAgent.
            LoadData(MedicamentSuggestions(List.empty))
      }
    })

  import context.dispatcher

  def syncRepo() = {
    Either.catchNonFatal {
      val pullComand = git.pull()
      pullComand.setRemote("origin")
      pullComand.setRemoteBranchName("master")
      pullComand.call()
    }.left.foreach(t => {
        log.info("Git data: {}", git.getRepository)
        log.error("Couldn't sync the database: {}", t)
      }
    )
  }

  def setupSystem(): Unit = {
    implicit val system = context.system
    implicit val materializer = ActorMaterializer()(context)
    syncRepo()
    patientManager ! LoadPatientData
    val webServer = new WebServer(patientManager, rememberInputAgent, config)
    webServer.start()
    log.info("Granger started patient manager")
    unstashAll()
    context.become(serverStarted(webServer))
    initialiseRememberAgent()
  }


  override def receive: Receive = {
    case Orchestrate =>
      setupSystem()
    case Ping => stash()
  }

  private[this] def serverStarted(webServer: WebServer): Receive = {
    case Orchestrator.Shutdown =>
      webServer.shutDown()
    case Ping => sender() ! Pong
    case other => log.info("Orchestrator is not accepting commands, lost {}", other)
  }

}

object Orchestrator {
  case object Orchestrate
  case object Ping
  case object Pong

  def props(config: GrangerConfig)
           (implicit git: Git, clock: Clock, baseDirProvider: BaseDirProvider): Props = {
    Props(new Orchestrator(config))
  }

  case object Shutdown

}
