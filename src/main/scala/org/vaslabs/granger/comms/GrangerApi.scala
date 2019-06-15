package org.vaslabs.granger.comms

import java.time.ZonedDateTime

import akka.http.scaladsl.model.StatusCode
import org.vaslabs.granger.{ CommandOutcome, Failure }
import org.vaslabs.granger.comms.UserApi._
import org.vaslabs.granger.comms.api.model._
import org.vaslabs.granger.modelv2.{ Patient, PatientId }
import org.vaslabs.granger.reminders.{ AllPatientReminders, DeletedAck, Notify, SnoozeAck }
import org.vaslabs.granger.repo.InvalidData

import scala.concurrent.Future

/**
 * Created by vnicolaou on 12/06/17.
 */
trait GrangerApi[F[_]] {
  type Response = Either[Failure, Patient]

  def addPatient(patient: Patient): F[Patient]

  def retrieveAllPatients(): F[List[Patient]]

  def addToothInfo(rq: AddToothInformationRequest): F[Response]

  def getLatestActivity(patientId: PatientId): F[Map[Int, List[Activity]]]

  def getPublicKey(): Future[PubKey]

  def initGitRepo(remoteRepo: RemoteRepo): Future[StatusCode]

  def startNewTreatment(startTreatment: StartTreatment): Future[Response]

  def finishTreatment(finishTreatment: FinishTreatment): Future[Response]

  def deleteTreatment(deleteTreatment: DeleteTreatment): F[Response]

  def deletePatient(patientId: PatientId): F[CommandOutcome]

  def treatmentNotifications(timestamp: ZonedDateTime): F[Notify]

  def rememberedData(): Future[AutocompleteSuggestions]

  def modifyReminder(rq: ModifyReminder): F[SnoozeAck]

  def deleteReminder(patientId: PatientId, timestamp: ZonedDateTime): F[DeletedAck]

  def allReminders(patientId: PatientId): F[AllPatientReminders]

}
