package org.vaslabs.granger

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.circe.{Decoder, Encoder}
import io.circe.java8._
/**
 * Created by vnicolaou on 28/05/17.
 */
object model {

  final class PatientId(val id: Long) extends AnyVal{
    override def toString: String = id.toString
  }

  object PatientId {

    @inline def apply(id: Long): PatientId = new PatientId(id)

    implicit val patientIdEncoder: Encoder[PatientId] =
      Encoder[Long].contramap(_.id)
    implicit val patientIdDecoder: Decoder[PatientId] =
      Decoder[Long].map(PatientId(_))
  }

  case class Patient(patientId: PatientId, firstName: String, lastName: String, dateOfBirth: LocalDate, dentalChart: DentalChart)

  case class DentalChart(teeth: List[Tooth])


  case class Root(size: Int, thickness: String, name: String)

  case class ToothDetails(roots: List[Root], medicament: String, nextVisit: String, notes: String)


  case class Tooth(number: Int, details: ToothDetails)

  object json {
    val localDateFormatter = DateTimeFormatter.ISO_DATE
    implicit val localDateEncoder: Encoder[LocalDate] =
      time.encodeLocalDate(localDateFormatter)
    implicit val localDateDecoder: Decoder[LocalDate] =
      time.decodeLocalDate(localDateFormatter)
  }

}
