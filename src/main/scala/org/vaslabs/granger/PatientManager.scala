package org.vaslabs.granger

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.vaslabs.granger.model.{Patient, PatientId, Treatment}
import org.vaslabs.granger.repo.{GrangerRepo, Repo}

import scala.concurrent.Future
import akka.pattern.pipe
import org.eclipse.jgit.api.Git
import org.vaslabs.granger.comms.api.model.{AddToothInformationRequest, RemoteRepo}
import org.vaslabs.granger.repo.git.GitRepo

import scala.concurrent.duration._
/**
  * Created by vnicolaou on 29/05/17.
  */

class PatientManager private (grangerRepo: GrangerRepo[Map[PatientId, Patient], Future], grangerConfig: GrangerConfig)(implicit gitApi: Git) extends Actor with ActorLogging{
  import context.dispatcher
  import PatientManager._

  implicit val gitRepo: GitRepo = new GitRepo(new File(grangerConfig.repoLocation), "patients.json")

  val gitRepoPusher: ActorRef = context.actorOf(GitRepoPusher.props(grangerRepo))

  override def receive: Receive = {
    case FetchAllPatients =>
      val senderRef = sender()
      grangerRepo.retrieveAllPatients() pipeTo senderRef
    case AddPatient(patient) =>
      val senderRef = sender()
      schedulePushJob()
      grangerRepo.addPatient(patient) pipeTo senderRef
    case rq: AddToothInformationRequest =>
      schedulePushJob()
      grangerRepo.addToothInfo(rq) pipeTo sender()
    case LatestActivity(patientId) => grangerRepo.getLatestActivity(patientId) pipeTo sender()
    case InitRepo(remoteRepo) =>
      grangerRepo.setUpRepo(remoteRepo) pipeTo sender()
      schedulePushJob()
    case StartTreatment(patientId, toothId, info) =>
      grangerRepo.startTreatment(patientId, toothId, info) pipeTo sender()
    case FinishTreatment(patientId, toothId) =>
      grangerRepo.finishTreatment(patientId, toothId) pipeTo sender()
  }

  private[this] def schedulePushJob(): Unit = {
    gitRepoPusher ! GitRepoPusher.PushChanges
  }

}

object PatientManager {
  def props(grangerRepo: GrangerRepo[Map[PatientId, Patient],Future], grangerConfig: GrangerConfig)(implicit gitApi: Git): Props = Props(new PatientManager(grangerRepo, grangerConfig))


  case object FetchAllPatients

  case class AddPatient(patient: Patient)

  case class LatestActivity(patientId: PatientId)

  case class InitRepo(remoteRepo: RemoteRepo)

  case class StartTreatment(patientId: PatientId, toothId: Int, info: String)
  case class FinishTreatment(patientId: PatientId, toothId: Int)

}
