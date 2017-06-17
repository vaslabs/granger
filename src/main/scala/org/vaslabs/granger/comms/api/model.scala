package org.vaslabs.granger.comms.api

import java.time.ZonedDateTime

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

  case class Activity(date: ZonedDateTime, tooth: Int, `type`: String)

  object Activity {
    implicit val ordering: Ordering[Activity] = (a1, a2) => {
      a2.date.compareTo(a1.date)
    }

    trait Transformer[A] {
      def transform(a: A): Activity
    }

    implicit final class ActivityConverter[A](val `class`: A)(implicit transformer: Transformer[A]) {
      def asActivity(): Activity =
        transformer.transform(`class`)
    }
  }

  case class PubKey(value: String)

  case class GitRepo(uri: String)
}
