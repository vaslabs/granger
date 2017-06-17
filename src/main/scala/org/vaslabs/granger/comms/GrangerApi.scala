package org.vaslabs.granger.comms

import org.vaslabs.granger.comms.api.model.{Activity, AddToothInformationRequest}
import org.vaslabs.granger.model.{Patient, PatientId}
import org.vaslabs.granger.repo.NotReady

/**
  * Created by vnicolaou on 12/06/17.
  */
trait GrangerApi[F[_]] {

  def addPatient(patient: Patient): F[Patient]

  def retrieveAllPatients(): F[Either[NotReady, List[Patient]]]

  def addToothInfo(rq: AddToothInformationRequest): F[Patient]

  def getLatestActivity(patientId: PatientId): F[List[Activity]]

}
