package org.vaslabs.granger

import java.time.{Clock, LocalDate, ZonedDateTime}
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

  case class Patient(patientId: PatientId, firstName: String, lastName: String, dateOfBirth: LocalDate, dentalChart: DentalChart) {
    def update(tooth: Tooth): Patient =
      copy(dentalChart = dentalChart.update(tooth))
    def update(id: Int, root: Root): Patient =
      dentalChart.teeth.find(_.number == id).map(_.update(root)).map(update(_))
        .getOrElse(this)
    def update(id: Int, toothNote: ToothNote): Patient =
      dentalChart.teeth.find(_.number == id).map(
        t => t.update(toothNote)
      ).map(update(_)).getOrElse(this)
  }

  case class DentalChart(teeth: List[Tooth]) {

    def update(tooth: Tooth): DentalChart = {
      DentalChart(tooth::teeth.filterNot(_.number == tooth.number))
    }

  }

  object DentalChart {
    def emptyChart()(implicit clock: Clock): DentalChart =
      DentalChart(((11 to 18) ++ (21 to 28) ++ (31 to 38) ++ (41 to 48)).map(
        Tooth(_, List.empty, List(ToothNote("", "", "", ZonedDateTime.now(clock))))
      ).toList.sorted)
  }


  case class Root(size: Int, thickness: String, name: String)

  case class ToothNote(medicament: String, nextVisit: String, notes: String, dateOfNote: ZonedDateTime)


  case class Tooth(number: Int, roots: List[Root], details: List[ToothNote]) {
    def update(root: Root): Tooth =
      copy(roots = root :: roots)
    def update(toothNote: ToothNote) =
      copy(details = toothNote :: details)
  }

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
    val zonedDateTimeFormatter = DateTimeFormatter.ISO_ZONED_DATE_TIME

    implicit val localDateEncoder: Encoder[LocalDate] =
      time.encodeLocalDate(localDateFormatter)
    implicit val localDateDecoder: Decoder[LocalDate] =
      time.decodeLocalDate(localDateFormatter)

    implicit val zonedDateTimeEncoder: Encoder[ZonedDateTime] =
      time.encodeZonedDateTime(zonedDateTimeFormatter)
    implicit val zonedDateTimeDecoder: Decoder[ZonedDateTime] =
      time.decodeZonedDateTime(zonedDateTimeFormatter)
  }

}
