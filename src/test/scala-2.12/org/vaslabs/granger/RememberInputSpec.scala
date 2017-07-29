package org.vaslabs.granger

import java.io.File
import java.time._

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import io.circe.{Decoder, Encoder}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.scalatest.FlatSpecLike
import org.vaslabs.granger.RememberInputAgent.{LoadData, MedicamentSuggestions, Suggestion}
import org.vaslabs.granger.comms.api.model.{AddToothInformationRequest, AutocompleteSuggestions}
import org.vaslabs.granger.modelv2.{Medicament, PatientId}
import org.vaslabs.granger.repo.git.{EmptyProvider, GitRepo}

/**
  * Created by vnicolaou on 29/07/17.
  */
class RememberInputSpec extends TestKit(ActorSystem("rememberInputTest")) with FlatSpecLike with ImplicitSender {
  val tmpDir = System.getProperty("java.io.tmpdir") + s"/${this.getClass.getName}/.granger_repo/"

  val config = GrangerConfig(tmpDir, keysLocation = "/tmp/.ssh")
  implicit val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

  import scala.concurrent.duration._
  implicit val timeout: Timeout = Timeout(2 seconds)
  val dbDirectory = new File(tmpDir)
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

  implicit val jsonSuggestionsEncoder: Encoder[MedicamentSuggestions] = Encoder[MedicamentSuggestions]
  implicit val jsonSuggestionsDecoder: Decoder[MedicamentSuggestions] = Decoder[MedicamentSuggestions]
  implicit val emptyRememberProvider: EmptyProvider[MedicamentSuggestions] = () => MedicamentSuggestions(List.empty)
  implicit val rememberRepo: GitRepo[MedicamentSuggestions] =
    new GitRepo[MedicamentSuggestions](new File(tmpDir), "remember.json")

  "remembered medicaments" should "be in the remember list" in {
    val rememberInputAgent = system.actorOf(RememberInputAgent.props(5))
    rememberInputAgent ! LoadData
    rememberInputAgent ! AddToothInformationRequest(
      PatientId(2), 2,
      Some(Medicament("someMed", ZonedDateTime.now(clock))),
      None, None,
      None, None,
      ZonedDateTime.now(clock))

    rememberInputAgent ! RememberInputAgent.Suggest
    expectMsg(AutocompleteSuggestions(List("someMed")))
  }
}
