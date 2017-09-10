package org.vaslabs.granger

import java.time.Clock

import akka.actor.{Actor, ActorLogging, PoisonPill, Props, Stash}
import akka.stream.ActorMaterializer
import org.eclipse.jgit.api.Git
import org.vaslabs.granger.PatientManager.{LoadData => LoadPatientData}
import org.vaslabs.granger.comms.WebServer
import org.vaslabs.granger.github.releases.{Release, ReleaseTag}
import org.vaslabs.granger.modelv2.{Patient, PatientId}
import org.vaslabs.granger.repo.{GrangerRepo, SingleStateGrangerRepo}
import org.vaslabs.granger.system.UpdateDownloader.{UpdateCompleted, ValidReleases}
import org.vaslabs.granger.system.{BaseDirProvider, UpdateChecker, UpdateDownloader}

import scala.concurrent.Future
import cats.syntax.either._
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode

/**
  * Created by vnicolaou on 28/08/17.
  */
class Orchestrator private (grangerRepo: GrangerRepo[Map[PatientId, Patient], Future], config: GrangerConfig)
                           (implicit git: Git, clock: Clock, baseDirProvider: BaseDirProvider)
  extends Actor with ActorLogging with Stash
{

  import Orchestrator._

  val updateDownloader = context.actorOf(UpdateDownloader.props(ReleaseTag.CURRENT, self), "updateDownloader")
  val updateChecker = context.actorOf(UpdateChecker.props(self), "updateChecker")
  val patientManager = context.actorOf(PatientManager.props(grangerRepo, config), "patientManager")

  import context.dispatcher

  def checkForUpdates(): Unit = {
    log.info("Checking for updates")
    updateChecker ! UpdateChecker.CheckForUpdates
  }

  override def preStart(): Unit = {
    checkForUpdates()
  }

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

  def warning(releases: List[Release]) = log.warning(
    "Too many releases ahead, please contact support! \n{}", releases)


  override def receive: Receive = {
    case Orchestrate =>
      context.become(checkingForUpdates)
    case Ping => stash()
  }

  private[this] def serverStarted(webServer: WebServer): Receive = {
    case Orchestrator.Shutdown =>
      webServer.shutDown()
    case Ping => sender() ! Pong
    case other => log.info("Orchestrator is not accepting commands, lost {}", other)
  }

  private[this] def checkingForUpdates: Receive = {
    case ValidReleases(releases) if releases.isEmpty =>
      log.info("Granger is up to date")
      setupSystem()
    case ValidReleases(releases) if releases.size == 1 =>
      updateDownloader ! releases(0).assets(0)
      log.warning("System will update and exit. Please wait and start again...")
      context.become(waitingForNewRelease)
    case ValidReleases(releases) =>
      warning(releases)
      setupSystem()
    case Ping => stash()
  }

  private[this] def waitingForNewRelease: Receive = {
    case UpdateCompleted =>
      log.warning("Please restart to use the new version of granger")
      self ! PoisonPill
    case Ping => stash()
    case _ => log.warning("System is being updated, please wait...")
  }
}

object Orchestrator {
  case object Orchestrate
  case object Ping
  case object Pong

  def props(grangerRepo: SingleStateGrangerRepo, config: GrangerConfig)
           (implicit git: Git, clock: Clock, baseDirProvider: BaseDirProvider): Props = {
    Props(new Orchestrator(grangerRepo, config))
  }

  case object Shutdown

}
