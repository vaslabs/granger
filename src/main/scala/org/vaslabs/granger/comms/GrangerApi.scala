package org.vaslabs.granger.comms

import akka.http.scaladsl.model.StatusCode
import org.vaslabs.granger.PatientManager.{FinishTreatment, StartTreatment}
import org.vaslabs.granger.comms.api.model.{
  Activity,
  AddToothInformationRequest,
  PubKey,
  RemoteRepo
}
import org.vaslabs.granger.modelv2.{Patient, PatientId}
import org.vaslabs.granger.repo.{RepoErrorState}

import scala.concurrent.Future

/**
  * Created by vnicolaou on 12/06/17.
  */
trait GrangerApi[F[_]] {

  def addPatient(patient: Patient): F[Patient]

  def retrieveAllPatients(): F[Either[RepoErrorState, List[Patient]]]

  def addToothInfo(rq: AddToothInformationRequest): F[Patient]

  def getLatestActivity(patientId: PatientId): F[Map[Int, List[Activity]]]

  def getPublicKey(): Future[PubKey]

  def initGitRepo(remoteRepo: RemoteRepo): Future[StatusCode]

  def startNewTreatment(startTreatment: StartTreatment): Future[Patient]

  def finishTreatment(finishTreatment: FinishTreatment): Future[Patient]

}
