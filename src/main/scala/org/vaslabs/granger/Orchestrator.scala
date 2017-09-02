package org.vaslabs.granger

import java.io.File
import java.time.Clock

import akka.actor.{Actor, ActorLogging, Props, Stash}
import akka.stream.ActorMaterializer
import org.eclipse.jgit.api.Git
import org.vaslabs.granger.PatientManager.{LoadData => LoadPatientData}
import org.vaslabs.granger.comms.WebServer
import org.vaslabs.granger.github.releases
import org.vaslabs.granger.github.releases.ReleaseTag
import org.vaslabs.granger.repo.{GrangerRepo, SingleStateGrangerRepo}
import org.vaslabs.granger.system.UpdateDownloader.ValidReleases
import org.vaslabs.granger.system.{GrangerDownloader, UpdateChecker, UpdateDownloader}
import cats.syntax.either._
import org.vaslabs.granger.modelv2.{Patient, PatientId}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by vnicolaou on 28/08/17.
  */
class Orchestrator private (grangerRepo: GrangerRepo[Map[PatientId, Patient], Future], config: GrangerConfig)
                           (implicit git: Git, clock: Clock)
  extends Actor with ActorLogging with Stash
{

  val updateDownloader = context.actorOf(UpdateDownloader.props(ReleaseTag.CURRENT, self), "updateDownloader")
  val updateChecker = context.actorOf(UpdateChecker.props(self), "updateChecker")
  val patientManager = context.actorOf(PatientManager.props(grangerRepo, config), "patientManager")

  import context.dispatcher

  def checkForUpdates(): Unit = {
    updateChecker ! UpdateChecker.CheckForUpdates
  }

  override def preStart(): Unit = {
    checkForUpdates()
  }

  def setupSystem(): Unit = {
    implicit val system = context.system
    implicit val materializer = ActorMaterializer()(context)
    patientManager ! LoadPatientData
    val webServer = new WebServer(patientManager, config)
    webServer.start()
    log.info("Granger started patient manager")
    context.become(serverStarted(webServer))
  }

  def deflateRelease(zipFile: File): Unit = ???

  def updateTo(release: releases.Release): Unit = ???

  def warning() = log.warning("Too many releases ahead, please contact support!")


  override def receive: Receive = {
    case Orchestrator.Orchestrate =>
      context.become(checkingForUpdates)
  }

  private[this] def serverStarted(webServer: WebServer): Receive = {
    case Orchestrator.Shutdown =>
      webServer.shutDown()
    case _ => log.info("Orchestrator is not accepting commands")
  }

  private[this] def checkingForUpdates: Receive = {
    case ValidReleases(releases) if releases.isEmpty =>
      log.info("Granger is up to date")
      setupSystem()
    case ValidReleases(releases) if releases.size == 1 =>
      updateTo(releases.head)
    case ValidReleases(releases) =>
      warning()
      setupSystem()
  }
}

object Orchestrator {
  case object Orchestrate

  def props(grangerRepo: SingleStateGrangerRepo, config: GrangerConfig)
           (implicit git: Git, clock: Clock): Props = {
    Props(new Orchestrator(grangerRepo, config))
  }

  case object Shutdown

}
