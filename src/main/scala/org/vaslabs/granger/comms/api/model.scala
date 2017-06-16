package org.vaslabs.granger.comms.api

import org.vaslabs.granger.model._

/**
  * Created by vnicolaou on 13/06/17.
  */
object model {

  case class AddToothInformationRequest(
                                       patientId: PatientId,
                                       toothNumber: Int,
                                       medicament: Option[Medicament],
                                       nextVisit: Option[NextVisit],
                                       roots: Option[List[Root]],
                                       toothNote: Option[ToothNote]
                                       )
}
