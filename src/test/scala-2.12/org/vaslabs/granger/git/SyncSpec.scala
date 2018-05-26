package org.vaslabs.granger.git

import java.io.File

import akka.testkit.TestActorRef
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.scalatest.{Assertion, Matchers}
import org.vaslabs.granger.{AkkaBaseSpec, Orchestrator}
import org.vaslabs.granger.modelv2.{PatientId}
import org.vaslabs.granger.repo.SingleStateGrangerRepo
import org.vaslabs.granger.system.BaseDirProvider

/**
  * Created by vnicolaou on 09/09/17.
  */
class SyncSpec extends AkkaBaseSpec("SyncTest") with Matchers{

  val remoteUri = s"${System.getProperty("java.io.tmpdir")}/remote_granger"
  import system.dispatcher

  implicit val baseDirProvider: BaseDirProvider = () => {
    new File(remoteUri)
  }


  override def afterAll() = {
    FileUtils.deleteDirectory(new File(remoteUri))
    super.afterAll()
  }

  def resetLocalRepository() = {
    FileUtils.forceDelete(new File(tmpDir))
    val newLocalRepo = new File(tmpDir)
    Git.init().setDirectory(dbDirectory)
      .setBare(false)
      .call()
    setRemote(s"file://$remoteUri")
  }

  def setRemote(remote: String) = {
    val remoteSetUrlCommand = git.remoteAdd()
    remoteSetUrlCommand.setName("origin")
    remoteSetUrlCommand.setUri(new URIish(remote))
    remoteSetUrlCommand.call()
  }

  def givenRemoteRepoWithData()(f : String => Assertion): Any = {
    val dir = new File(remoteUri)
    dir.mkdir()
    Git.init().setDirectory(dir)
      .setBare(true)
      .call()
    setRemote(s"file://${remoteUri}")
    gitRepo.save("Sample save", Map(PatientId(1) -> withNewPatient()))
    git.push().call()
    resetLocalRepository()
    f(remoteUri)
  }

  "given granger starts up it" should "sync with remote repo" in {
    givenRemoteRepoWithData() {
      uri => {
        val orchestrator = TestActorRef(Orchestrator.props(config))
        orchestrator ! Orchestrator.Orchestrate
        orchestrator ! Orchestrator.Ping
        expectMsg(Orchestrator.Pong)
        assert(gitRepo.getState().toOption.get.size == 1)
      }
    }
  }
}
