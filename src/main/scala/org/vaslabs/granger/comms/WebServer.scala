package org.vaslabs.granger.comms

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCode
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContext, Future}
import akka.pattern._
import akka.util.Timeout
import org.vaslabs.granger.GrangerConfig
import org.vaslabs.granger.PatientManager.{AddPatient, FetchAllPatients, InitRepo, LatestActivity}
import org.vaslabs.granger.comms.api.model
import org.vaslabs.granger.comms.api.model.{Activity, PubKey}
import org.vaslabs.granger.model.{Patient, PatientId, Tooth}
import org.vaslabs.granger.repo.NotReady

import scala.concurrent.duration._
import scala.io.Source
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

  def getLatestActivity(patientId: PatientId): Future[Map[Int, List[Activity]]] =
    (patientManager ? LatestActivity(patientId)).mapTo[Map[Int, List[Activity]]]

  def getPublicKey(): Future[PubKey] =
    Future {
      val keyValue = Source.fromFile(s"${config.keysLocation}/id_rsa.pub").mkString
      PubKey(keyValue)
    }

  override def initGitRepo(gitRepo: model.GitRepo): Future[StatusCode] =
    (patientManager ? InitRepo(gitRepo)).mapTo[StatusCode]
}
