package org.vaslabs.granger

import java.io.File
import java.time.Clock

import akka.actor.ActorSystem
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.vaslabs.granger.repo.SingleStateGrangerRepo
import org.vaslabs.granger.system.GrangerDownloader
import pureconfig._
/**
  * Created by vnicolaou on 04/06/17.
  */
object Main {

  def main(args: Array[String]): Unit = {


    implicit val system = ActorSystem()
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

        val orchestrator = system.actorOf(Orchestrator.props(grangerRepo, config))



        orchestrator ! Orchestrator.Orchestrate

        sys.addShutdownHook(
          {
            println("Shutting down")
            orchestrator ! Orchestrator.Shutdown
          }
        )
      }).left.foreach(
      println(_)
    )
  }
}
