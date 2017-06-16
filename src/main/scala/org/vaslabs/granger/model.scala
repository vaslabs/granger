package org.vaslabs.granger

import java.time.{Clock, LocalDate, ZonedDateTime}
import java.time.format.DateTimeFormatter

import io.circe.{Decoder, Encoder}
import io.circe.java8._
import io.circe.generic.semiauto._


import scala.util.Try
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
    def extractLatestActivity: List[Activity] = {
      dentalChart.teeth.flatMap(_.allActivity()).sorted
    }

    def update(tooth: Tooth): Patient =
      copy(dentalChart = dentalChart.update(tooth))

    def update(patientId: PatientId): Patient = {
      copy(patientId, dentalChart = DentalChart.emptyChart())
    }
  }

  case class DentalChart(teeth: List[Tooth]) {

    def update(tooth: Tooth): DentalChart = {
      DentalChart((tooth::teeth.filterNot(_.number == tooth.number)).sorted)
    }

  }

  object DentalChart {
    def emptyChart(): DentalChart =
      DentalChart(((11 to 18) ++ (21 to 28) ++ (31 to 38) ++ (41 to 48)).map(
        Tooth(_)
      ).toList.sorted)
  }


  case class Root(size: Int, thickness: String, name: String)

  case class Activity(date: ZonedDateTime, tooth: Int, `type`: String)

  object Activity {
    implicit val ordering: Ordering[Activity] = (a1, a2) => {
      a2.date.compareTo(a1.date)
    }
  }

  case class Medicament(name: String, date: ZonedDateTime)

  private[this] def isNullOrEmpty(name: String): Boolean = name == null || name.isEmpty

  private[this] def verifyNonEmptyString[A](value: String, a: A): Either[String, A] = {
    if (isNullOrEmpty(value))
      Left("Value is empty")
    else
      Right(a)
  }

  object Medicament {

    import json._

    implicit val medicamentDecoder: Decoder[Option[Medicament]] = deriveDecoder[Medicament].map(
      medicament => verifyNonEmptyString(medicament.name, medicament).toOption
    )
  }

  case class NextVisit(notes: String, dateOfNextVisit: ZonedDateTime, dateOfNote: ZonedDateTime)

  object NextVisit {
    import json._
    implicit val nextVisitDecoder: Decoder[Option[NextVisit]] = deriveDecoder[NextVisit].map(
      nv => verifyNonEmptyString[NextVisit](nv.notes, nv).toOption
    )
  }

  case class ToothNote(note: String, dateOfNote: ZonedDateTime)

  object ToothNote {
    import json._
    implicit val toothNoteDecoder: Decoder[Option[ToothNote]] = deriveDecoder[ToothNote].map(
      tn => verifyNonEmptyString[ToothNote](tn.note, tn).toOption
    )
  }


  case class Tooth(number: Int, roots: List[Root] = List.empty,
                   notes: List[ToothNote] = List.empty,
                   medicaments: List[Medicament] = List.empty,
                   nextVisits: List[NextVisit] = List.empty) {
    def update(rootList: Option[List[Root]], medicament: Option[Medicament], nextVisit: Option[NextVisit], note: Option[ToothNote]): Tooth = {
      println(s"Updating with ${rootList}, ${medicament}, ${nextVisit}, ${note}")
      val newRoots = rootList.getOrElse(roots)
      val newMedicaments = medicament.map(_::medicaments).getOrElse(medicaments)
      val newNextVisits = nextVisit.map(_::nextVisits).getOrElse(nextVisits)
      val newNotes = note.map(_::notes).getOrElse(notes)
      copy(roots = newRoots, medicaments = newMedicaments, nextVisits = newNextVisits, notes = newNotes)
    }

    def allActivity(): List[Activity] = {
      val notesActivity: List[Activity] = notes.map(note => Activity(note.dateOfNote, number, "Note"))
      val medicamentsActivity: List[Activity] = medicaments.map(m => Activity(m.date, number, "Medicament"))
      val nextVisitsActivity: List[Activity] = nextVisits.map(nv => Activity(nv.dateOfNote, number, "Next visit note"))
      notesActivity ++ medicamentsActivity ++ nextVisitsActivity
    }
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
