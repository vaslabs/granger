package org.vaslabs.granger

import java.io.{File, FileWriter, PrintWriter}
import java.time.{Clock, Instant, ZoneOffset}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.ActorMaterializer
import akka.util.Timeout
import io.circe.{Decoder, Encoder}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.scalatest.{Assertion, AsyncFlatSpecLike, BeforeAndAfterAll, Matchers}
import org.vaslabs.granger.PatientManager.LoadData
import org.vaslabs.granger.comms.{HttpRouter, WebServer}
import org.vaslabs.granger.modelv2.{Patient, PatientId}
import org.vaslabs.granger.repo.SingleStateGrangerRepo
import org.vaslabs.granger.repo.git.{EmptyProvider, GitRepo}

import scala.concurrent.Await

/**
  * Created by vnicolaou on 28/06/17.
  */
trait BaseSpec extends AsyncFlatSpecLike with Matchers with BeforeAndAfterAll with ScalatestRouteTest {
  val tmpDir = System.getProperty("java.io.tmpdir") + s"/${this.getClass.getName}/.granger_repo/"

  val config = GrangerConfig(tmpDir, keysLocation = "/tmp/.ssh")

  implicit val jsonPatientsEncoder: Encoder[Map[PatientId, Patient]] = Encoder[Map[PatientId, Patient]]
  implicit val jsonPatientsDecoder: Decoder[Map[PatientId, Patient]] = Decoder[Map[PatientId, Patient]]
  implicit val emptyPatientsProvider: EmptyProvider[Map[PatientId, Patient]] = () => Map.empty

  val gitRepo: GitRepo[Map[PatientId, Patient]] = new GitRepo(new File(tmpDir), "patients.json")

  override def beforeAll() = {
    val dir = new File(tmpDir)
    dir.mkdir()
    val file = new File(tmpDir + "patients.json")
    file.createNewFile()
    val writer = new PrintWriter(new FileWriter(file))
    writer.print("{}")
    writer.close()
  }
  import scala.concurrent.duration._

  override def afterAll(): Unit = {
    import org.apache.commons.io.FileUtils
    val dir = new File(tmpDir)
    FileUtils.deleteDirectory(dir)
    Await.ready(Http().shutdownAllConnectionPools() andThen {
      case _ => system.terminate().foreach(_ => println("Shutdown system"))
    }, 2 seconds)
  }

  implicit val clock:  Clock = Clock.fixed(Instant.ofEpochMilli(0), ZoneOffset.UTC)
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

  import akka.pattern._

  def withHttpRouter[F[_]](actorSystem: ActorSystem, grangerConfig: GrangerConfig)(f: HttpRouter => F[Assertion]): F[Assertion] = {
    implicit val system = actorSystem
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    val grangerRepo = new SingleStateGrangerRepo()

    val patientManager = actorSystem.actorOf(PatientManager.props(grangerRepo, config))
    patientManager ! LoadData

    val httpRouter = new WebServer(patientManager, grangerConfig) with HttpRouter
    httpRouter.start()
    f(httpRouter)
  }

}
