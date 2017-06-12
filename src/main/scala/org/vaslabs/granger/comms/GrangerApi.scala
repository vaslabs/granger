package org.vaslabs.granger.comms

import org.vaslabs.granger.model.{Patient, PatientId, Tooth}

/**
  * Created by vnicolaou on 12/06/17.
  */
trait GrangerApi[F[_]] {

  def addPatient(patient: Patient): F[Patient]

  def retrieveAllPatients(): F[List[Patient]]

  def addToothDetails(patientId: PatientId, tooth: Tooth): F[Patient]

}
