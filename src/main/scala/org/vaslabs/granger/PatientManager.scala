package org.vaslabs.granger

import akka.actor.{Actor, ActorLogging}
import org.vaslabs.granger.model.Patient
import org.vaslabs.granger.repo.GrangerRepo

import scala.concurrent.Future

/**
  * Created by vnicolaou on 29/05/17.
  */
object FetchAllPatients
case class AddPatient(patient: Patient)
class PatientManager(implicit val grangerRepo: GrangerRepo[Future]) extends Actor with ActorLogging{
  import context.dispatcher
  override def receive: Receive = {
    case FetchAllPatients =>
      sender() ! grangerRepo.retrieveAllPatients()
    case AddPatient(patient) =>
      grangerRepo.addPatient(patient)

  }
}
