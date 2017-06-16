package org.vaslabs.granger.comms

import org.vaslabs.granger.comms.api.model.AddToothInformationRequest
import org.vaslabs.granger.model.{Activity, Patient, PatientId}

/**
  * Created by vnicolaou on 12/06/17.
  */
trait GrangerApi[F[_]] {

  def addPatient(patient: Patient): F[Patient]

  def retrieveAllPatients(): F[List[Patient]]

  def addToothInfo(rq: AddToothInformationRequest): F[Patient]

  def getLatestActivity(patientId: PatientId): F[List[Activity]]

}
