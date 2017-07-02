package org.vaslabs.granger.repo

import akka.http.scaladsl.model.StatusCode
import org.vaslabs.granger.PatientManager.LoadDataOutcome
import org.vaslabs.granger.comms.api.model.{Activity, AddToothInformationRequest}
import org.vaslabs.granger.modelv2._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by vnicolaou on 28/05/17.
  */

case class PatientEntry(patientId: PatientId, patient: Patient)
case class NotReady(error: String)

trait GrangerRepo[State, F[_]] {

  implicit val executionContext: ExecutionContext

  def loadData()(implicit repo: Repo[Map[PatientId, Patient]]): F[LoadDataOutcome]

  def setUpRepo(repoRq: Any)(implicit repo: Repo[State]): Future[StatusCode] =
    Future {
      repo.setUp(repoRq)
    }

  def getLatestActivity(patientId: PatientId): F[Map[Int, List[Activity]]]

  def addToothInfo(rq: AddToothInformationRequest)(implicit repo: Repo[State]): F[Patient]

  def addPatient(patient: Patient)(implicit repo: Repo[State]): F[Patient]

  def retrieveAllPatients()(implicit repo: Repo[State]): Future[Either[NotReady, List[Patient]]]

  def pushChanges()(implicit repo: Repo[State]): Future[Unit] =
    Future {
      repo.push()
    }


  def startTreatment(patientId: PatientId, toothId: Int, info: String)(implicit repo: Repo[State]): Future[Patient]

  def finishTreatment(patientId: PatientId, toothId: Int)(implicit repo: Repo[State]): Future[Patient]

}
