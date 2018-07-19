package org.vaslabs.granger

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike}

/**
  * Created by vnicolaou on 30/07/17.
  */
abstract class AkkaBaseSpec(name: String) extends
  TestKit(ActorSystem(name))
  with BaseSpec
  with ImplicitSender with BeforeAndAfterAll {

  import system.dispatcher

  override def afterAll() = {
    system.terminate().foreach(_ => println("Shutdown system"))
    super.afterAll()
  }

  override def beforeAll() = {
    super.beforeAll()
  }
}
