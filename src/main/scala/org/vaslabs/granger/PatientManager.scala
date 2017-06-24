package org.vaslabs.granger

import akka.actor.{Actor, ActorLogging, Props}
import org.vaslabs.granger.model.{Patient, PatientId}
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
      schedulePushJob()
      grangerRepo.retrieveAllPatients() pipeTo senderRef
    case AddPatient(patient) =>
      val senderRef = sender()
      grangerRepo.addPatient(patient) pipeTo senderRef
    case rq: AddToothInformationRequest =>
      grangerRepo.addToothInfo(rq) pipeTo sender()
    case LatestActivity(patientId) => grangerRepo.getLatestActivity(patientId) pipeTo sender()
    case InitRepo(gitRepo) =>
      grangerRepo.setUpRepo(gitRepo) pipeTo sender()
    case PushChanges =>
      grangerRepo.pushChanges()
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

}
