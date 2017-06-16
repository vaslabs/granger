package org.vaslabs.granger

import akka.actor.{Actor, ActorLogging, Props}
import org.vaslabs.granger.model.{Patient, PatientId, Tooth}
import org.vaslabs.granger.repo.GrangerRepo

import scala.concurrent.Future
import akka.pattern.pipe
import cats.syntax.either._
import org.vaslabs.granger.comms.api.model.AddToothInformationRequest
/**
  * Created by vnicolaou on 29/05/17.
  */

class PatientManager private (grangerRepo: GrangerRepo[Future]) extends Actor with ActorLogging{
  import context.dispatcher
  import PatientManager._
  override def receive: Receive = {
    case FetchAllPatients =>
      val senderRef = sender()
      grangerRepo.retrieveAllPatients() pipeTo senderRef
    case AddPatient(patient) =>
      val senderRef = sender()
      grangerRepo.addPatient(patient) pipeTo senderRef
    case rq: AddToothInformationRequest =>
      grangerRepo.addToothInfo(rq) pipeTo sender()
    case LatestActivity(patientId) => grangerRepo.getLatestActivity(patientId) pipeTo sender()
  }
}

object PatientManager {
  def props(grangerRepo: GrangerRepo[Future]): Props = Props(new PatientManager(grangerRepo))

  object FetchAllPatients

  case class AddPatient(patient: Patient)

  case class LatestActivity(patientId: PatientId)

}
