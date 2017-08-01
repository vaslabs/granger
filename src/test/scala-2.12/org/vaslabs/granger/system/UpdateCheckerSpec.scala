package org.vaslabs.granger.system

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.{FlatSpecLike, Matchers}
import org.vaslabs.granger.github.releases.ReleaseTag
import org.vaslabs.granger.system.UpdateDownloader.ValidReleases

/**
  * Created by vnicolaou on 01/08/17.
  */
class UpdateCheckerSpec extends TestKit(ActorSystem("updaterTest")) with FlatSpecLike with Matchers with ImplicitSender{

  "when doing a request for the releases it" should "respond with the valid releases" in {
    val downloaderProbe = TestProbe()
    val updateChecker = TestActorRef(UpdateChecker.props(downloaderProbe.ref))
    updateChecker ! UpdateChecker.CheckForUpdates
    val validReleases = downloaderProbe.expectMsgType[ValidReleases]

    {
      var downloading = false
      implicit val downloader: Downloader = (url) => {
        downloading = true
        val parts = url.split("/")
        Right(new File(s"${parts.apply(parts.size-2)}/${parts.apply(parts.size-1)}"))
      }
      val updaterProbe = TestProbe()
      val updateDownloader = TestActorRef(UpdateDownloader.props(ReleaseTag("1.3"), updaterProbe.ref))
      updateDownloader ! validReleases
      updaterProbe.expectMsg(new File("1.4/granger.zip"))
    }
  }

}
