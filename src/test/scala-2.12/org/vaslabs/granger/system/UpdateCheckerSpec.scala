package org.vaslabs.granger.system

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.{FlatSpecLike, Matchers}
import org.vaslabs.granger.github.releases.ReleaseTag
import org.vaslabs.granger.system.UpdateDownloader.ValidReleases

import scala.concurrent.duration._
/**
  * Created by vnicolaou on 01/08/17.
  */
class UpdateCheckerSpec extends TestKit(ActorSystem("updaterTest")) with FlatSpecLike with Matchers with ImplicitSender{

  implicit val timeout: Timeout = Timeout(2 minutes)


  "when doing a request for the releases it" should "respond with the valid releases" in {

    implicit val baseDirProvider: BaseDirProvider = () => {
      new File(s"${sys.env.get("HOME").get}")
    }

    val downloaderProbe = TestProbe()
    val updateChecker = TestActorRef(UpdateChecker.props(downloaderProbe.ref))
    updateChecker ! UpdateChecker.CheckForUpdates
    val validReleases = downloaderProbe.expectMsgType[ValidReleases].validReleases
    assert(validReleases.size == 1)

    {
      var downloading = false
      val updaterProbe = TestProbe()
      val updateDownloader = TestActorRef(UpdateDownloader.props(ReleaseTag("1.41"), updaterProbe.ref))
      updateDownloader ! validReleases
      updaterProbe.expectMsg(new File(s"${sys.env.get("HOME").get}/granger"))
    }
  }

}
