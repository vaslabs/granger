package org.vaslabs.granger.repo

import akka.http.scaladsl.model.StatusCode
import org.vaslabs.granger.comms.api.model
import org.vaslabs.granger.comms.api.model.{Activity, AddToothInformationRequest}
import org.vaslabs.granger.model.{Patient, PatientId}

import scala.concurrent.Future

/**
  * Created by vnicolaou on 28/05/17.
  */

case class PatientEntry(patientId: PatientId, patient: Patient)
case class NotReady(error: String)

trait GrangerRepo[F[_]] {
  def setUpRepo(gitRepo: model.GitRepo): Future[StatusCode]

  def getLatestActivity(patientId: PatientId): F[Map[Int, List[Activity]]]

  def addToothInfo(rq: AddToothInformationRequest): F[Patient]

  def addPatient(patient: Patient): F[Patient]

  def retrieveAllPatients(): F[Either[NotReady, List[Patient]]]

}
