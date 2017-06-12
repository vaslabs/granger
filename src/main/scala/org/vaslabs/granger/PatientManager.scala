package org.vaslabs.granger

import akka.actor.{Actor, ActorLogging, Props}
import org.vaslabs.granger.model.Patient
import org.vaslabs.granger.repo.GrangerRepo

import scala.concurrent.Future
import akka.pattern.pipe
import cats.syntax.either._
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
      println(s"Adding patient ${patient}")
      grangerRepo.addPatient(patient) pipeTo senderRef

  }
}

object PatientManager {
  def props(grangerRepo: GrangerRepo[Future]): Props = Props(new PatientManager(grangerRepo))

  object FetchAllPatients

  case class AddPatient(patient: Patient)
}
