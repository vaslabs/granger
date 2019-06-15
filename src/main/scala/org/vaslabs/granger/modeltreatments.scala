package org.vaslabs.granger

/**
 * Created by vnicolaou on 09/07/17.
 */
object modeltreatments {

  sealed trait TreatmentCategory

  case class RootCanalTreatment() extends TreatmentCategory {
    override def toString: String = "Re-RCT"
  }

  case class RepeatRootCanalTreatment() extends TreatmentCategory {
    override def toString: String = "RCT"
  }
}
