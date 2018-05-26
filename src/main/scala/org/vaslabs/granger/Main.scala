package org.vaslabs.granger

import java.io.File
import java.time.Clock

import akka.actor.ActorSystem
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.vaslabs.granger.repo.SingleStateGrangerRepo
import org.vaslabs.granger.system.BaseDirProvider
import pureconfig._
/**
  * Created by vnicolaou on 04/06/17.
  */
object Main extends App{


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

      implicit val baseDirProvider:BaseDirProvider = () => {
        val workingDirectory = System.getProperty("user.dir")
        new File(workingDirectory).getParentFile.getParentFile
      }

      val orchestrator = system.actorOf(Orchestrator.props(config), "orchestrator")



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
