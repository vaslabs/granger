package org.vaslabs.granger

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.vaslabs.granger.comms.WebServer

/**
  * Created by vnicolaou on 04/06/17.
  */
object Main {

  def main(args: Array[String]): Unit = {
    sys.addShutdownHook(
      println("Shutting down")
    )

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    implicit val executionContext = system.dispatcher

    val repository = {
      val builder = new FileRepositoryBuilder
      builder.setGitDir(new File("~/.granger_repo"))
        .readEnvironment()
        .findGitDir()
        .build()
    }



    implicit val git: Git = new Git(repository)


    val webServer = new WebServer()
    webServer.start()
  }
}
