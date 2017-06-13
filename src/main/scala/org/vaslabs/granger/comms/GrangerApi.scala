package org.vaslabs.granger.comms

import org.vaslabs.granger.PatientManager.{AddToothNote, AddToothRoot}
import org.vaslabs.granger.comms.api.model.{RootRequest, ToothNoteRequest}
import org.vaslabs.granger.model
import org.vaslabs.granger.model.{Patient, PatientId, Tooth}

import scala.concurrent.Future

/**
  * Created by vnicolaou on 12/06/17.
  */
trait GrangerApi[F[_]] {

  def addPatient(patient: Patient): F[Patient]

  def retrieveAllPatients(): F[List[Patient]]

  def addToothNotes(toothNoteRequest: AddToothNote): F[Patient]

  def addToothRoots(rootRequest: AddToothRoot): Future[model.Patient]

}
