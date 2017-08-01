package org.vaslabs.granger.system

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.vaslabs.granger.github.releases.{Asset, Release, ReleaseTag}

class UpdateDownloader private(currentRelease: ReleaseTag, updater: ActorRef)(implicit downloader: Downloader) extends Actor with ActorLogging {
  import UpdateDownloader.ValidReleases
  override def receive: Receive = {
    case ValidReleases(releases) =>
      val releasesAhead = releases.filter(_.tag_name.greaterThan(currentRelease)).sorted
      releasesAhead.headOption.map(
       _.assets.headOption.map(self ! _)
      )
    case Asset(artifactUrl) =>
      log.info("Update to {}", artifactUrl)
      downloader.download(artifactUrl).map(updater ! _)
  }
}

object UpdateDownloader {
  case class ValidReleases(validReleases: List[Release])

  def props(currentRelease: ReleaseTag, updater: ActorRef)(implicit downloader: Downloader): Props = {
    Props(new UpdateDownloader(currentRelease, updater))
  }
}