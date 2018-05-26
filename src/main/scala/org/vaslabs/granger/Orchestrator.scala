package org.vaslabs.granger

import java.time.Clock

import akka.actor.{Actor, ActorLogging, Props, Stash}
import akka.stream.ActorMaterializer
import org.eclipse.jgit.api.Git
import org.vaslabs.granger.PatientManager.{LoadData => LoadPatientData}
import org.vaslabs.granger.comms.WebServer
import org.vaslabs.granger.system.BaseDirProvider

import cats.syntax.either._

/**
  * Created by vnicolaou on 28/08/17.
  */
class Orchestrator private (config: GrangerConfig)
                           (implicit git: Git, clock: Clock, baseDirProvider: BaseDirProvider)
  extends Actor with ActorLogging with Stash
{

  import Orchestrator._

  val patientManager = context.actorOf(PatientManager.props(config), "patientManager")

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
    val webServer = new WebServer(patientManager, config)
    webServer.start()
    log.info("Granger started patient manager")
    unstashAll()
    context.become(serverStarted(webServer))
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
