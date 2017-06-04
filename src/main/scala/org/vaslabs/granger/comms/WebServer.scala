package org.vaslabs.granger.comms

import java.io.File

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.vaslabs.granger.PatientManager
import org.vaslabs.granger.repo.GitBasedGrangerRepo

import scala.concurrent.ExecutionContext

/**
 * Created by vnicolaou on 28/05/17.
 */

class WebServer()(implicit gitApi: Git, executionContext: ExecutionContext, actorSystem: ActorSystem, materializer: ActorMaterializer) extends GitBasedGrangerRepo with HttpRouter {


  val patientManager = actorSystem.actorOf(PatientManager.props()(this))


  def start(): Unit = {
    Http().bindAndHandle(routes, "0.0.0.0", 8080)
  }

}
