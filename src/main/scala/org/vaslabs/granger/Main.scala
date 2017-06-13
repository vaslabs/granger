package org.vaslabs.granger

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.vaslabs.granger.comms.WebServer
import org.vaslabs.granger.repo.GitBasedGrangerRepo

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



    val dbDirectory = new File("/home/vnicolaou/.granger_repo")

    if (!dbDirectory.exists()) {
      Git.init().setDirectory(dbDirectory)
        .setBare(false)
      .call()
    }

    val repository = {
      val builder = new FileRepositoryBuilder
      builder.setMustExist(true).findGitDir(dbDirectory)
      .build()
    }





    implicit val git: Git = new Git(repository)

    val grangerRepo = new GitBasedGrangerRepo(dbDirectory)

    val patientManager = system.actorOf(PatientManager.props(grangerRepo))


    val webServer = new WebServer(patientManager)
    webServer.start()
  }
}
