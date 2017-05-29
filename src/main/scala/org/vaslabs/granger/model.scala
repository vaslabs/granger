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

  object Tooth {
    implicit val ordering: Ordering[Tooth] = (x: Tooth, y: Tooth) => {
      if (x.number <= 18 && y.number <= 18)
        y.number - x.number
      else if (x.number <= 18)
        -1
      else if (y.number <= 18)
        1
      else if (x.number <= 28 && y.number <= 28)
        x.number - y.number
      else if (x.number <= 28)
        -1
      else if (y.number <= 28)
        1
      else if (x.number <= 38 && y.number <= 38)
        x.number - y.number
      else if (y.number <= 38)
        -1
      else if (x.number <= 38)
        1
      else
        y.number - x.number
    }
  }

  object json {
    val localDateFormatter = DateTimeFormatter.ISO_DATE
    implicit val localDateEncoder: Encoder[LocalDate] =
      time.encodeLocalDate(localDateFormatter)
    implicit val localDateDecoder: Decoder[LocalDate] =
      time.decodeLocalDate(localDateFormatter)
  }

}
