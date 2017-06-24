package org.vaslabs.granger.comms

import akka.http.scaladsl.model.StatusCode
import org.vaslabs.granger.comms.api.model.{Activity, AddToothInformationRequest, GitRepo, PubKey}
import org.vaslabs.granger.model.{Patient, PatientId}
import org.vaslabs.granger.repo.NotReady

import scala.concurrent.Future

/**
  * Created by vnicolaou on 12/06/17.
  */
trait GrangerApi[F[_]] {

  def addPatient(patient: Patient): F[Patient]

  def retrieveAllPatients(): F[Either[NotReady, List[Patient]]]

  def addToothInfo(rq: AddToothInformationRequest): F[Patient]

  def getLatestActivity(patientId: PatientId): F[Map[Int, List[Activity]]]

  def getPublicKey(): Future[PubKey]

  def initGitRepo(gitRepo: GitRepo): Future[StatusCode]
}
