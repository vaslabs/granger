package org.vaslabs.granger.comms

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import org.vaslabs.granger.model

import scala.concurrent.{ExecutionContext, Future}
import akka.pattern._
import akka.util.Timeout
import org.vaslabs.granger.PatientManager.{AddPatient, AddToothNote, AddToothRoot, FetchAllPatients}
import org.vaslabs.granger.comms.api.model
import org.vaslabs.granger.comms.api.model.{RootRequest, ToothNoteRequest}
import org.vaslabs.granger.model.{Patient, PatientId, Tooth}

import scala.concurrent.duration._
/**
 * Created by vnicolaou on 28/05/17.
 */

class WebServer(patientManager: ActorRef)(implicit executionContext: ExecutionContext, actorSystem: ActorSystem, materializer: ActorMaterializer) extends GrangerApi[Future] with HttpRouter {

  implicit val timeout = Timeout(5 seconds)

  def start(): Unit = {
    Http().bindAndHandle(routes, "0.0.0.0", 8080)
  }

  override def addPatient(patient: Patient): Future[Patient] =
    (patientManager ? AddPatient(patient)).mapTo[Patient]

  override def retrieveAllPatients(): Future[List[Patient]] =
    (patientManager ? FetchAllPatients).mapTo[List[Patient]]

  override def addToothNotes(addToothNote: AddToothNote): Future[Patient] =
    (patientManager ? addToothNote).mapTo[Patient]

  override def addToothRoots(addRootRequest: AddToothRoot): Future[Patient] =
    (patientManager ? addRootRequest).mapTo[Patient]
}
