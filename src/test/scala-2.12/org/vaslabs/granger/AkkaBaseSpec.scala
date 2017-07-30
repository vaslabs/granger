package org.vaslabs.granger

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike}

/**
  * Created by vnicolaou on 30/07/17.
  */
abstract class AkkaBaseSpec(name: String) extends
  TestKit(ActorSystem(name))
  with BaseSpec with FlatSpecLike
  with BeforeAndAfterAll with ImplicitSender{

  import system.dispatcher

  override def afterAll() = {
    super.afterAll()
    system.terminate().foreach(_ => println("Shutdown system"))
  }

  override def beforeAll() = {
    super.beforeAll()
  }
}
