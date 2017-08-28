package org.vaslabs.granger

import java.io.File

import akka.actor.{Actor, ActorLogging, Stash}
import org.vaslabs.granger.github.releases
import org.vaslabs.granger.github.releases.ReleaseTag
import org.vaslabs.granger.system.UpdateDownloader.ValidReleases
import org.vaslabs.granger.system.{Downloader, UpdateChecker, UpdateDownloader}

/**
  * Created by vnicolaou on 28/08/17.
  */
class Orchestrator private () extends Actor with ActorLogging with Stash{

  implicit val downloader: Downloader = ???

  val updateDownloader = context.actorOf(UpdateDownloader.props(ReleaseTag.CURRENT, self), "updateDownloader")
  val updateChecker = context.actorOf(UpdateChecker.props(self), "updateChecker")

  def checkForUpdates(): Unit = {
    updateChecker ! UpdateChecker.CheckForUpdates
  }

  override def preStart(): Unit = {
    checkForUpdates()
  }

  def setupSystem(): Unit = ???

  def deflateRelease(zipFile: File): Unit = ???

  def updateTo(release: releases.Release): Unit = {
    val asset = release.assets.head
    downloader.download(asset.browser_download_url).map(deflateRelease(_))
  }

  def warning() = log.warning("Too many releases ahead, please contact support!")

  override def receive: Receive = {
    case ValidReleases(releases) if releases.isEmpty =>
      setupSystem()
    case ValidReleases(releases) if releases.size == 1 =>
      updateTo(releases.head)
    case ValidReleases(releases) =>
      warning()
      setupSystem()
  }
}

object Orchestrator {

}
