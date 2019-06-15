package org.vaslabs.granger.comms.api

import java.time.ZonedDateTime

import io.circe.{ Decoder, Encoder }
import org.vaslabs.granger.modelv2._

import io.circe.generic.semiauto._

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
      toothNote: Option[TreatmentNote],
      obturation: Option[List[Root]],
      treatmentStarted: ZonedDateTime)
  object AddToothInformationRequest {
    import org.vaslabs.granger.v2json._
    import io.circe.generic.auto._
    implicit val encoder: Encoder[AddToothInformationRequest] = deriveEncoder[AddToothInformationRequest]
    implicit val decoder: Decoder[AddToothInformationRequest] = deriveDecoder[AddToothInformationRequest]
  }

  case class Activity(date: ZonedDateTime, tooth: Int, `type`: String)

  object Activity {
    implicit val ordering: Ordering[Activity] = (lhsActivity: Activity, rhsActivity: Activity) => {
      rhsActivity.date.compareTo(lhsActivity.date)
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

  case class RemoteRepo(uri: String)

  case class AutocompleteSuggestions(medicamentNames: List[String])
}
