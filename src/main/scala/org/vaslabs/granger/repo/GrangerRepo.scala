package org.vaslabs.granger.repo

import java.time.ZonedDateTime

import akka.http.scaladsl.model.StatusCode
import cats.effect.IO
import org.vaslabs.granger.PatientManager.LoadDataOutcome
import org.vaslabs.granger.comms.api.model.{Activity, AddToothInformationRequest}
import org.vaslabs.granger.modeltreatments.TreatmentCategory
import org.vaslabs.granger.modelv2._

import scala.concurrent.ExecutionContext

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

  def loadData(): F[LoadDataOutcome]

  def setUpRepo(repoRq: Any): F[StatusCode]

  def getLatestActivity(patientId: PatientId): Map[Int, List[Activity]]

  def addToothInfo(rq: AddToothInformationRequest): Either[InvalidData, F[Patient]]

  def addPatient(patient: Patient): F[Patient]

  def retrieveAllPatients(): IO[Either[RepoErrorState, List[Patient]]]

  def pushChanges(): F[Unit]

  def startTreatment(patientId: PatientId, toothId: Int, treatmentCategory: TreatmentCategory):
    Either[InvalidData, F[Patient]]

  def finishTreatment(patientId: PatientId, toothId: Int): Either[InvalidData, F[Patient]]

  def deleteTreatment(patientId: PatientId, toothId: Int, timestamp: ZonedDateTime):
    Either[InvalidData, F[Patient]]

  def deletePatient(patientId: PatientId): F[Either[IOError, Unit]]



}
