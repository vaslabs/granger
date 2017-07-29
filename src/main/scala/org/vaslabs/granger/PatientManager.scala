package org.vaslabs.granger

import java.io.File
import java.time.Clock

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import org.vaslabs.granger.repo.{
  EmptyRepo,
  GrangerRepo,
  RepoErrorState,
  UnparseableSchema
}

import scala.concurrent.Future
import akka.pattern.pipe
import org.eclipse.jgit.api.Git
import org.vaslabs.granger.comms.api.model.{
  AddToothInformationRequest,
  RemoteRepo
}
import org.vaslabs.granger.modeltreatments.TreatmentCategory
import org.vaslabs.granger.modelv2.PatientId
import org.vaslabs.granger.repo.git.GitRepo

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

  implicit val gitRepo: GitRepo =
    new GitRepo(new File(grangerConfig.repoLocation), "patients.json")

  val gitRepoPusher: ActorRef =
    context.actorOf(GitRepoPusher.props(grangerRepo), "gitPusher")

  val rememberInputAgent: ActorRef =
    context.actorOf(RememberInputAgent.props(5), "rememberInputAgent")



  override def receive: Receive = {
    case LoadData =>
      grangerRepo.loadData() pipeTo self
    case LoadDataSuccess =>
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
      grangerRepo.setUpRepo(remoteRepo) pipeTo sender()
      schedulePushJob()
  }

  def receivePostLoad: Receive = {
    case FetchAllPatients =>
      val senderRef = sender()
      grangerRepo.retrieveAllPatients() pipeTo senderRef
    case AddPatient(patient) =>
      val senderRef = sender()
      schedulePushJob()
      grangerRepo.addPatient(patient) pipeTo senderRef
    case rq: AddToothInformationRequest =>
      schedulePushJob()
      rememberInputAgent ! rq
      grangerRepo.addToothInfo(rq) pipeTo sender()
    case LatestActivity(patientId) =>
      grangerRepo.getLatestActivity(patientId) pipeTo sender()
    case StartTreatment(patientId, toothId, category) =>
      schedulePushJob()
      grangerRepo.startTreatment(patientId, toothId, category) pipeTo sender()
    case FinishTreatment(patientId, toothId) =>
      schedulePushJob()
      grangerRepo.finishTreatment(patientId, toothId) pipeTo sender()
    case RememberedData =>
      rememberInputAgent forward RememberInputAgent.Suggest

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
  case class FinishTreatment(patientId: PatientId, toothId: Int)

  case object LoadData
  sealed trait LoadDataOutcome
  case object LoadDataSuccess extends LoadDataOutcome
  case class LoadDataFailure(repoState: RepoErrorState) extends LoadDataOutcome

  case object RememberedData
}
