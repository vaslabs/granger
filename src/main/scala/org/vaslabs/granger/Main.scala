package org.vaslabs.granger

import java.io.File
import java.time.Clock

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.vaslabs.granger.PatientManager.LoadData
import org.vaslabs.granger.comms.WebServer
import org.vaslabs.granger.repo.SingleStateGrangerRepo
import pureconfig._
/**
  * Created by vnicolaou on 04/06/17.
  */
object Main {

  def main(args: Array[String]): Unit = {


    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val clock: Clock = Clock.systemUTC()

    implicit val executionContext = system.dispatcher

    loadConfig[GrangerConfig](GrangerConfig.Namespace).map(
      config => {

        val dbDirectory = new File(config.repoLocation)

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

        val grangerRepo = new SingleStateGrangerRepo()

        val patientManager = system.actorOf(PatientManager.props(grangerRepo, config))

        patientManager ! LoadData


        val webServer = new WebServer(patientManager, config)
        webServer.start()

        sys.addShutdownHook(
          {
            println("Shutting down")
            webServer.shutDown().foreach(_ => println("Http service is shut down"))
          }
        )
      }).left.foreach(
      println(_)
    )
  }
}
