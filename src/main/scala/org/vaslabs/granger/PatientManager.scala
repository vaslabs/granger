package org.vaslabs.granger

import akka.actor.{Actor, ActorLogging, Props}
import org.vaslabs.granger.model.Patient
import org.vaslabs.granger.repo.GrangerRepo

import scala.concurrent.Future
import akka.pattern.pipe
/**
  * Created by vnicolaou on 29/05/17.
  */

class PatientManager private (implicit grangerRepo: GrangerRepo[Future]) extends Actor with ActorLogging{
  import context.dispatcher
  import PatientManager._
  override def receive: Receive = {
    case FetchAllPatients =>
      val senderRef = sender()
      grangerRepo.retrieveAllPatients() pipeTo senderRef
    case AddPatient(patient) =>
      grangerRepo.addPatient(patient)

  }
}

object PatientManager {
  def props()(implicit grangerRepo: GrangerRepo[Future]): Props = Props(new PatientManager())

  object FetchAllPatients

  case class AddPatient(patient: Patient)
}
