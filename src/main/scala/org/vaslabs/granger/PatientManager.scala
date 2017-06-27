package org.vaslabs.granger

import akka.actor.{Actor, ActorLogging, Props}
import org.vaslabs.granger.model.{Patient, PatientId, Treatment}
import org.vaslabs.granger.repo.GrangerRepo

import scala.concurrent.Future
import akka.pattern.pipe
import org.vaslabs.granger.comms.api.model.{AddToothInformationRequest, GitRepo}

import scala.concurrent.duration._
/**
  * Created by vnicolaou on 29/05/17.
  */

class PatientManager private (grangerRepo: GrangerRepo[Future]) extends Actor with ActorLogging{
  import context.dispatcher
  import PatientManager._

  var pushScheduled = false

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
    case InitRepo(gitRepo) =>
      grangerRepo.setUpRepo(gitRepo) pipeTo sender()
      schedulePushJob()
    case PushChanges =>
      grangerRepo.pushChanges()
      pushScheduled = false
    case StartTreatment(patientId, toothId, info) =>
      grangerRepo.startTreatment(patientId, toothId, info) pipeTo sender()
    case FinishTreatment(patientId, toothId) =>
      grangerRepo.finishTreatment(patientId, toothId) pipeTo sender()
  }

  private[this] def schedulePushJob(): Unit = {
    if (!pushScheduled) {
      pushScheduled = true
      context.system.scheduler.scheduleOnce(15 seconds, self, PushChanges)
    }
  }

}

object PatientManager {
  def props(grangerRepo: GrangerRepo[Future]): Props = Props(new PatientManager(grangerRepo))

  private case object PushChanges

  case object FetchAllPatients

  case class AddPatient(patient: Patient)

  case class LatestActivity(patientId: PatientId)

  case class InitRepo(gitRepo: GitRepo)

  case class StartTreatment(patientId: PatientId, toothId: Int, info: String)
  case class FinishTreatment(patientId: PatientId, toothId: Int)

}
