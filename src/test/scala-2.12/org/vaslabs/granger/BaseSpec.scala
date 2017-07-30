package org.vaslabs.granger

import java.io.{File, FileWriter, PrintWriter}
import java.time.{Clock, Instant, ZoneOffset, ZonedDateTime}

import akka.http.scaladsl.Http
import akka.util.Timeout
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.scalatest.BeforeAndAfterAll
import org.vaslabs.granger.PatientManager.StartTreatment
import org.vaslabs.granger.RememberInputAgent.MedicamentSuggestions
import org.vaslabs.granger.modeltreatments.{RootCanalTreatment, TreatmentCategory}
import org.vaslabs.granger.modelv2.{DentalChart, Patient, PatientId, Treatment}
import org.vaslabs.granger.repo.git.{EmptyProvider, GitRepo}

import scala.concurrent.Await

/**
  * Created by vnicolaou on 28/06/17.
  */
trait BaseSpec { this: BeforeAndAfterAll =>
  val tmpDir = System.getProperty("java.io.tmpdir") + s"/${this.getClass.getName}-${System.currentTimeMillis()}/.granger_repo/"


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
  }

  implicit val emptyPatientsProvider: EmptyProvider[Map[PatientId, Patient]] = () => Map.empty

  import io.circe.generic.auto._
  implicit val jsonSuggestionsEncoder: Encoder[MedicamentSuggestions] = Encoder[MedicamentSuggestions]
  implicit val jsonSuggestionsDecoder: Decoder[MedicamentSuggestions] = Decoder[MedicamentSuggestions]

  implicit val emptyRememberProvider: EmptyProvider[MedicamentSuggestions] = () => MedicamentSuggestions(List.empty)
  implicit val rememberRepo: GitRepo[MedicamentSuggestions] =
    new GitRepo[MedicamentSuggestions](new File(tmpDir), "remember.json")



  def tearDown(): Unit = {
    import org.apache.commons.io.FileUtils
    val dir = new File(tmpDir)
    FileUtils.deleteDirectory(dir)
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

  val config = GrangerConfig(tmpDir, keysLocation = "/tmp/.ssh")

  {
    val dir = new File(tmpDir)
    dir.mkdir()
    val file = new File(tmpDir + "patients.json")
    file.createNewFile()
    val writer = new PrintWriter(new FileWriter(file))
    writer.print("{}")
    writer.close()
  }

  val repository = {
    val builder = new FileRepositoryBuilder
    builder.setMustExist(true).findGitDir(dbDirectory)
      .build()
  }
  implicit val git: Git = new Git(repository)

  import v2json._

  val gitRepo: GitRepo[Map[PatientId, Patient]] =
    new GitRepo[Map[PatientId, Patient]](
      new File(tmpDir),
      "patients.json"
    )

  def withNewPatient(
                      firstName: String = "FirstName",
                      lastName: String = "LastName",
                      dentalChart: DentalChart = DentalChart(List.empty)): Patient =
    Patient(
      PatientId(0), firstName, lastName,
      ZonedDateTime.now(clock).toLocalDate, dentalChart
    )

  def withPatient(patientId: PatientId,
                  firstName: String = "FirstName",
                  lastName: String = "LastName",
                  dentalChart: DentalChart = DentalChart.emptyChart()
                 ): Patient =
    Patient(
      patientId, firstName, lastName,
      ZonedDateTime.now(clock).toLocalDate, dentalChart
    )

  def withStartTreatment(
                          patientId: PatientId,
                          toothNumber: Int,
                          treatment: TreatmentCategory = RootCanalTreatment()) =
    StartTreatment(patientId, toothNumber, treatment)

  def withCompletedTreatment(treatmentCategory: TreatmentCategory = RootCanalTreatment()): Treatment =
    Treatment(ZonedDateTime.now(clock),
      Some(ZonedDateTime.now(clock)),
      treatmentCategory)

  def withOpenTreatment(treatmentCategory: TreatmentCategory = RootCanalTreatment()): Treatment =
    Treatment(ZonedDateTime.now(clock),
      None,
      treatmentCategory)
}
