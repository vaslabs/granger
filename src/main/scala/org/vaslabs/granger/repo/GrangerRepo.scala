package org.vaslabs.granger.repo

import java.time.ZonedDateTime

import akka.http.scaladsl.model.StatusCode
import org.vaslabs.granger.PatientManager.{CommandOutcome, LoadDataOutcome}
import org.vaslabs.granger.comms.api.model.{Activity, AddToothInformationRequest}
import org.vaslabs.granger.modeltreatments.TreatmentCategory
import org.vaslabs.granger.modelv2._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by vnicolaou on 28/05/17.
  */
case class PatientEntry(patientId: PatientId, patient: Patient)

sealed trait RepoErrorState

case class SchemaFailure(error: String) extends RepoErrorState
case object EmptyRepo extends RepoErrorState
case class UnkownState(error: String) extends RepoErrorState
case class UnparseableSchema(error: String) extends RepoErrorState

trait GrangerRepo[State, F[_]] {


  implicit val executionContext: ExecutionContext

  def loadData()(
      implicit repo: Repo[Map[PatientId, Patient]]): F[LoadDataOutcome]

  def setUpRepo(repoRq: Any)(implicit repo: Repo[State]): Future[StatusCode] =
    Future {
      repo.setUp(repoRq)
    }

  def getLatestActivity(patientId: PatientId): F[Map[Int, List[Activity]]]

  def addToothInfo(rq: AddToothInformationRequest)(
      implicit repo: Repo[State]): Patient

  def addPatient(patient: Patient)(implicit repo: Repo[State]): Patient

  def retrieveAllPatients()(implicit repo: Repo[State])
    : Future[Either[RepoErrorState, List[Patient]]]

  def pushChanges()(implicit repo: Repo[State]): Future[Unit] =
    Future {
      repo.push()
    }

  def startTreatment(patientId: PatientId,
                     toothId: Int,
                     treatmentCategory: TreatmentCategory)(
      implicit repo: Repo[State]): Patient

  def finishTreatment(patientId: PatientId, toothId: Int)(
      implicit repo: Repo[State]): Patient

  def deleteTreatment(patientId: PatientId, toothId: Int, timestamp: ZonedDateTime)
                     (implicit repo: Repo[Map[PatientId, Patient]]): Patient


}
