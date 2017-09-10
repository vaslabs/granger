package org.vaslabs.granger

import java.io.File
import java.time.{Clock, ZonedDateTime}

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import org.vaslabs.granger.repo.{EmptyRepo, GrangerRepo, RepoErrorState}

import scala.concurrent.Future
import akka.pattern.pipe
import io.circe.{Decoder, Encoder}
import org.eclipse.jgit.api.Git
import org.vaslabs.granger.RememberInputAgent.MedicamentSuggestions
import org.vaslabs.granger.comms.api.model.{AddToothInformationRequest, RemoteRepo}
import org.vaslabs.granger.modeltreatments.TreatmentCategory
import org.vaslabs.granger.modelv2.{Patient, PatientId}
import org.vaslabs.granger.repo.git.{EmptyProvider, GitRepo}

import cats.syntax.either._
/**
  * Created by vnicolaou on 29/05/17.
  */
class PatientManager private (
    grangerRepo: GrangerRepo[Map[modelv2.PatientId, modelv2.Patient], Future],
    grangerConfig: GrangerConfig)(implicit gitApi: Git, clock: Clock)
    extends Actor
    with ActorLogging {
  import context.dispatcher
  import PatientManager._


  import v2json._
  import io.circe.generic.auto._
  import io.circe.generic.semiauto._

  implicit val emptyPatientsProvider: EmptyProvider[Map[PatientId, Patient]] = () => Map.empty

  implicit val jsonSuggestionsEncoder: Encoder[MedicamentSuggestions] = deriveEncoder[MedicamentSuggestions]
  implicit val jsonSuggestionsDecoder: Decoder[MedicamentSuggestions] = deriveDecoder[MedicamentSuggestions]
  implicit val emptyRememberProvider: EmptyProvider[MedicamentSuggestions] = () => MedicamentSuggestions(List.empty)

  implicit val gitRepo: GitRepo[Map[PatientId, Patient]] =
    new GitRepo[Map[PatientId, Patient]](new File(grangerConfig.repoLocation), "patients.json")

  val gitRepoPusher: ActorRef =
    context.actorOf(GitRepoPusher.props(grangerRepo), "gitPusher")


  implicit val rememberRepo: GitRepo[MedicamentSuggestions] =
    new GitRepo[MedicamentSuggestions](new File(grangerConfig.repoLocation), "remember.json")

  val rememberInputAgent: ActorRef =
    context.actorOf(RememberInputAgent.props(5), "rememberInputAgent")


  def initialiseRememberAgent() =
    rememberRepo.getState().map(sm => rememberInputAgent ! RememberInputAgent.LoadData(sm))
      .left.foreach(error => {
      error match {
        case EmptyRepo =>
          rememberRepo.saveNew()
          rememberInputAgent ! RememberInputAgent.
            LoadData(MedicamentSuggestions(List.empty))
      }
    })

  override def receive: Receive = {
    case LoadData =>
      log.info("Loading patient data...")
      grangerRepo.loadData() pipeTo self
    case LoadDataSuccess =>
      log.info("Done")
      initialiseRememberAgent()
      context.become(receivePostLoad)
    case LoadDataFailure(repoState) =>
      repoState match {
        case EmptyRepo =>
          context.become(settingUp)
        case _ =>
          log.error("Failed to load repo: " + repoState)
          self ! PoisonPill
      }
  }

  def settingUp: Receive = {
    case FetchAllPatients =>
      grangerRepo.retrieveAllPatients() pipeTo sender()
    case InitRepo(remoteRepo) =>
      context.become(receivePostLoad)
      initialiseRememberAgent()
      grangerRepo.setUpRepo(remoteRepo) pipeTo sender()
  }


  def receivePostLoad: Receive = {
    getInformation orElse submitInformation
  }

  private [this] def getInformation: Receive = {
    case FetchAllPatients =>
      val senderRef = sender()
      grangerRepo.retrieveAllPatients() pipeTo senderRef
    case LatestActivity(patientId) =>
      grangerRepo.getLatestActivity(patientId) pipeTo sender()
    case m: MedicamentSuggestions =>
      rememberRepo.save(s"persisting suggestions with new medicament ${m.medicamentsUsed.apply(0).medicamentName}", m)
  }

  private [this] def submitInformation: Receive = {
    case AddPatient(patient) =>
      val senderRef = sender()
      schedulePushJob()
      grangerRepo.addPatient(patient) pipeTo senderRef
    case rq: AddToothInformationRequest =>
      schedulePushJob()
      rememberInputAgent ! rq
      grangerRepo.addToothInfo(rq) pipeTo sender()
    case StartTreatment(patientId, toothId, category) =>
      schedulePushJob()
      grangerRepo.startTreatment(patientId, toothId, category) pipeTo sender()
    case FinishTreatment(patientId, toothId) =>
      schedulePushJob()
      grangerRepo.finishTreatment(patientId, toothId) pipeTo sender()
    case RememberedData =>
      rememberInputAgent forward RememberInputAgent.Suggest
    case DeleteTreatment(patientId, toothId, timestamp) =>
      log.error("Deleting treatment {},{},{}", patientId, toothId, timestamp)
      schedulePushJob()
      grangerRepo.deleteTreatment(patientId, toothId, timestamp) pipeTo sender()

  }

  private[this] def schedulePushJob(): Unit = {
    gitRepoPusher ! GitRepoPusher.PushChanges
  }

}

object PatientManager {
  def props(grangerRepo: GrangerRepo[Map[modelv2.PatientId, modelv2.Patient],
                                     Future],
            grangerConfig: GrangerConfig)(implicit gitApi: Git,
                                          clock: Clock): Props =
    Props(new PatientManager(grangerRepo, grangerConfig))

  case object FetchAllPatients

  case class AddPatient(patient: modelv2.Patient)

  case class LatestActivity(patientId: PatientId)

  case class InitRepo(remoteRepo: RemoteRepo)

  case class StartTreatment(patientId: PatientId,
                            toothId: Int,
                            category: TreatmentCategory)
  case class DeleteTreatment(patientId:PatientId, toothId: Int, createdOn: ZonedDateTime)
  case class FinishTreatment(patientId: PatientId, toothId: Int)

  case object LoadData
  sealed trait LoadDataOutcome
  case object LoadDataSuccess extends LoadDataOutcome
  case class LoadDataFailure(repoState: RepoErrorState) extends LoadDataOutcome

  case object RememberedData

  sealed trait CommandOutcome
  case object Success extends CommandOutcome
  case class Failure(reason: String) extends CommandOutcome
}
