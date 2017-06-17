package org.vaslabs.granger.comms

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContext, Future}
import akka.pattern._
import akka.util.Timeout
import org.vaslabs.granger.GrangerConfig
import org.vaslabs.granger.PatientManager.{AddPatient, FetchAllPatients, LatestActivity}
import org.vaslabs.granger.comms.api.model
import org.vaslabs.granger.comms.api.model.Activity
import org.vaslabs.granger.model.{Patient, PatientId, Tooth}
import org.vaslabs.granger.repo.NotReady

import scala.concurrent.duration._
/**
 * Created by vnicolaou on 28/05/17.
 */

class WebServer(patientManager: ActorRef, config: GrangerConfig)(implicit executionContext: ExecutionContext, actorSystem: ActorSystem, materializer: ActorMaterializer) extends GrangerApi[Future] with HttpRouter {

  implicit val timeout = Timeout(5 seconds)

  def start(): Unit = {
    Http().bindAndHandle(routes, config.bindAddress, config.bindPort)
  }

  override def addPatient(patient: Patient): Future[Patient] =
    (patientManager ? AddPatient(patient)).mapTo[Patient]

  override def retrieveAllPatients(): Future[Either[NotReady, List[Patient]]] =
    (patientManager ? FetchAllPatients).mapTo[Either[NotReady, List[Patient]]]

  override def addToothInfo(rq: model.AddToothInformationRequest): Future[Patient] = {
    (patientManager ? rq).mapTo[Patient]
  }

  def getLatestActivity(patientId: PatientId): Future[List[Activity]] =
    (patientManager ? LatestActivity(patientId)).mapTo[List[Activity]]

}
