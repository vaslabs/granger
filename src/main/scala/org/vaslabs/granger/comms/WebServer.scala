package org.vaslabs.granger.comms

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import org.vaslabs.granger.repo.mock.MockGrangerRepo

/**
 * Created by vnicolaou on 28/05/17.
 */
object WebServer extends MockGrangerRepo with HttpRouter {

  def start(): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    Http().bindAndHandle(routes, "0.0.0.0", 8080)
  }


  def main(args: Array[String]): Unit = {
    sys.addShutdownHook(
      println("Shutting down")
    )

    start()
  }


}
