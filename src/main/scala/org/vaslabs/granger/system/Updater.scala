package org.vaslabs.granger.system

import akka.Done
import akka.actor.{Actor, ActorLogging, Props}
import org.vaslabs.granger.github.releases.{Release, ReleaseTag}

class Updater private (currentRelease: ReleaseTag) extends Actor with ActorLogging {
  import Updater.ValidReleases
  override def receive: Receive = {
    case ValidReleases(releases) =>
      val releasesAhead = releases.filter(_.tag_name.greaterThan(currentRelease)).sorted
      log.info("Would have updated a chain of {} releases: {}", releasesAhead.size, releasesAhead)
  }
}

object Updater {
  case class ValidReleases(validReleases: List[Release])

  def props(currentRelease: ReleaseTag): Props = {
    Props(new Updater(currentRelease))
  }
}