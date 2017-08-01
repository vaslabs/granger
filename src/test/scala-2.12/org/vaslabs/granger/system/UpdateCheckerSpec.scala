package org.vaslabs.granger.system

import akka.Done
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.{FlatSpecLike, Matchers}
import org.vaslabs.granger.system.Updater.ValidReleases

/**
  * Created by vnicolaou on 01/08/17.
  */
class UpdateCheckerSpec extends TestKit(ActorSystem("updaterTest")) with FlatSpecLike with Matchers with ImplicitSender{

  "when doing a request for the releases it" should "respond with the valid releases" in {
    val updaterProbe = TestProbe()
    val updateChecker = TestActorRef(UpdateChecker.props(updaterProbe.ref))
    updateChecker ! UpdateChecker.CheckForUpdates
    updaterProbe.expectMsgType[ValidReleases]
  }

}
