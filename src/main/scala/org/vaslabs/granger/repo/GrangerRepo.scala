package org.vaslabs.granger.repo

import org.vaslabs.granger.comms.api.model.{RootRequest, ToothNoteRequest}
import org.vaslabs.granger.model
import org.vaslabs.granger.model.{Patient, PatientId, Tooth}

import scala.concurrent.Future

/**
  * Created by vnicolaou on 28/05/17.
  */

case class PatientEntry(patientId: PatientId, patient: Patient)

trait GrangerRepo[F[_]] {
  def addPatient(patient: Patient): F[Patient]

  def retrieveAllPatients(): F[List[Patient]]

  def addToothNotes(patientId: PatientId, toothNoteRequest: ToothNoteRequest): F[Patient]

  def addToothRoots(patientId: model.PatientId, rootRequest: RootRequest): Future[model.Patient]


}
