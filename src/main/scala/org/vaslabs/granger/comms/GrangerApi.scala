package org.vaslabs.granger.comms

import java.time.ZonedDateTime

import akka.http.scaladsl.model.StatusCode
import org.vaslabs.granger.PatientManager._
import org.vaslabs.granger.comms.api.model._
import org.vaslabs.granger.modelv2.{Patient, PatientId}
import org.vaslabs.granger.reminders.RCTReminderActor.Protocol.External._

import scala.concurrent.Future

/**
  * Created by vnicolaou on 12/06/17.
  */
trait GrangerApi[F[_]] {

  def addPatient(patient: Patient): F[Patient]

  def retrieveAllPatients(): F[List[Patient]]

  def addToothInfo(rq: AddToothInformationRequest): F[Patient]

  def getLatestActivity(patientId: PatientId): F[Map[Int, List[Activity]]]

  def getPublicKey(): Future[PubKey]

  def initGitRepo(remoteRepo: RemoteRepo): Future[StatusCode]

  def startNewTreatment(startTreatment: StartTreatment): Future[Either[Failure, Patient]]

  def finishTreatment(finishTreatment: FinishTreatment): Future[Patient]

  def deleteTreatment(deleteTreatment: DeleteTreatment): F[Patient]

  def deletePatient(patientId: PatientId): F[CommandOutcome]

  def treatmentNotifications(timestamp: ZonedDateTime): F[Notify]

  def rememberedData(): Future[AutocompleteSuggestions]

  def modifyReminder(rq: ModifyReminder): F[SnoozeAck]

  def deleteReminder(patientId: PatientId, timestamp: ZonedDateTime): F[DeletedAck]

  def allReminders(patientId: PatientId): F[AllPatientReminders]

}
