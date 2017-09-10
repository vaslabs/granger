package org.vaslabs.granger.comms

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCode
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContext, Future}
import akka.pattern._
import akka.util.Timeout
import org.vaslabs.granger.GrangerConfig
import org.vaslabs.granger.PatientManager._
import org.vaslabs.granger.comms.api.model._
import org.vaslabs.granger.modelv2._
import org.vaslabs.granger.repo.RepoErrorState

import scala.concurrent.duration._
import scala.io.Source

/**
  * Created by vnicolaou on 28/05/17.
  */
class WebServer(patientManager: ActorRef, config: GrangerConfig)(
    implicit executionContext: ExecutionContext,
    actorSystem: ActorSystem,
    materializer: ActorMaterializer)
    extends GrangerApi[Future]
    with HttpRouter {

  implicit val timeout = Timeout(5 seconds)

  def start(): Unit = {
    Http().bindAndHandle(routes, config.bindAddress, config.bindPort)
  }

  def shutDown(): Future[Unit] = {
    Http().shutdownAllConnectionPools() andThen {
      case _ => actorSystem.terminate()
    }
  }

  override def addPatient(patient: Patient): Future[Patient] =
    (patientManager ? AddPatient(patient)).mapTo[Patient]

  override def retrieveAllPatients()
    : Future[Either[RepoErrorState, List[Patient]]] =
    (patientManager ? FetchAllPatients)
      .mapTo[Either[RepoErrorState, List[Patient]]]

  override def addToothInfo(rq: AddToothInformationRequest): Future[Patient] = {
    (patientManager ? rq).mapTo[Patient]
  }

  def getLatestActivity(
      patientId: PatientId): Future[Map[Int, List[Activity]]] =
    (patientManager ? LatestActivity(patientId))
      .mapTo[Map[Int, List[Activity]]]

  def getPublicKey(): Future[PubKey] =
    Future {
      val keyValue =
        Source.fromFile(s"${config.keysLocation}/id_rsa.pub").mkString
      PubKey(keyValue)
    }

  override def initGitRepo(remoteRepo: RemoteRepo): Future[StatusCode] =
    (patientManager ? InitRepo(remoteRepo)).mapTo[StatusCode]

  override def startNewTreatment(
      startTreatment: StartTreatment): Future[Patient] =
    (patientManager ? startTreatment).mapTo[Patient]

  override def finishTreatment(
      finishTreatment: FinishTreatment): Future[Patient] =
    (patientManager ? finishTreatment).mapTo[Patient]

  override def rememberedData(): Future[AutocompleteSuggestions] = {
    (patientManager ? RememberedData).mapTo[AutocompleteSuggestions]
  }

  override def deleteTreatment(deleteTreatment: DeleteTreatment): Future[CommandOutcome] =
    (patientManager ? deleteTreatment).map(_ => Success)
}
