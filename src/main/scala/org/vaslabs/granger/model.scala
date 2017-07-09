package org.vaslabs.granger

import java.time.{Clock, LocalDate, ZonedDateTime}
import java.time.format.DateTimeFormatter

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import io.circe.java8._
import org.vaslabs.granger.comms.api.model.Activity
import io.circe.generic.semiauto._
import io.circe.generic.auto._
/**
 * Created by vnicolaou on 28/05/17.
 */
object model {
  import org.vaslabs.granger.json._

  import Activity._
  final class PatientId(val id: Long) extends AnyVal{
    override def toString: String = id.toString
  }

  object PatientId {

    @inline def apply(id: Long): PatientId = new PatientId(id)

    implicit val patientIdEncoder: Encoder[PatientId] =
      Encoder[Long].contramap(_.id)
    implicit val patientIdDecoder: Decoder[PatientId] =
      Decoder[Long].map(PatientId(_))

    implicit val patientIdKeyDecoder: KeyDecoder[PatientId] = KeyDecoder[Long].map(PatientId(_))
    implicit val patientIdKeyEncoder: KeyEncoder[PatientId] = KeyEncoder[Long].contramap[PatientId](_.id)
  }

  case class Patient(patientId: PatientId, firstName: String, lastName: String, dateOfBirth: LocalDate, dentalChart: DentalChart) {

    def extractLatestActivity: Map[Int, List[Activity]] = {
      dentalChart.teeth.flatMap(_.allActivity()).groupBy(_.tooth).mapValues(_.sorted)
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

  case class Medicament(name: String, date: ZonedDateTime)

  object Medicament {

    implicit val medicamentDecoder: Decoder[Option[Medicament]] = deriveDecoder[Option[Medicament]].map(
      o => o.flatMap(medicament => verifyNonEmptyString(medicament.name, medicament).toOption)
    )
  }

  private[this] def isNullOrEmpty(name: String): Boolean = name == null || name.isEmpty

  def verifyNonEmptyString[A](value: String, a: A): Either[String, A] = {
    if (isNullOrEmpty(value))
      Left("Value is empty")
    else
      Right(a)
  }


  case class NextVisit(notes: String, dateOfNextVisit: ZonedDateTime, dateOfNote: ZonedDateTime)

  object NextVisit {

    implicit val nextVisitDecoder: Decoder[Option[NextVisit]] = deriveDecoder[Option[NextVisit]].map(
      o => o.flatMap(nv => verifyNonEmptyString[NextVisit](nv.notes, nv).toOption)
    )
  }

  case class ToothNote(note: String, dateOfNote: ZonedDateTime)

  object ToothNote {
    implicit val toothNoteDecoder: Decoder[Option[ToothNote]] = deriveDecoder[Option[ToothNote]].map(
      o => o.flatMap(tn => verifyNonEmptyString[ToothNote](tn.note, tn).toOption)
    )
  }


  case class Tooth(number: Int, roots: List[Root] = List.empty,
                   notes: List[ToothNote] = List.empty,
                   medicaments: List[Medicament] = List.empty,
                   nextVisits: List[NextVisit] = List.empty,
                   _treatments: Option[List[Treatment]] = None) {

    lazy val treatments: List[Treatment] = _treatments.getOrElse(List.empty)

    def update(rootList: Option[List[Root]], medicament: Option[Medicament], nextVisit: Option[NextVisit], note: Option[ToothNote]): Tooth = {
      val newRoots = rootList.getOrElse(roots)
      val newMedicaments = medicament.map(_::medicaments).getOrElse(medicaments)
      val newNextVisits = nextVisit.map(_::nextVisits).getOrElse(nextVisits)
      val newNotes = note.map(_::notes).getOrElse(notes)
      copy(roots = newRoots, medicaments = newMedicaments, nextVisits = newNextVisits, notes = newNotes)
    }

    def update(treatment: Treatment): Either[Treatment, Tooth] = {
      if (treatments.size == 0)
        Right(copy(_treatments = Some(List(treatment))))
      else
        treatments.head.dateCompleted.map(_ => Right(copy(_treatments = Some(treatment :: treatments)))).getOrElse(Left(treatments.head))
    }

    def finishTreatment()(implicit clock: Clock): Option[Tooth] = {
      treatments.headOption.flatMap(
        treatment =>
          treatment.dateCompleted.fold[Option[Treatment]]
            (Some(treatment.copy(dateCompleted = Some(ZonedDateTime.now(clock)))))
            (_ => None)
      ).map(
        t => {
          t :: treatments.drop(1)
        }
      ).map(ts => copy(_treatments = Some(ts)))
    }


    implicit val m_transformer: Transformer[Medicament] = (a: Medicament) => Activity(a.date, number, "Medicament")
    implicit val nv_transformer: Transformer[NextVisit] = (nv: NextVisit) => Activity(nv.dateOfNote, number, "Next visit note")
    implicit val tn_transformer: Transformer[ToothNote] = (n: ToothNote) => Activity(n.dateOfNote, number, "Note")
    implicit val t_transformer: Transformer[Treatment] = (t: Treatment) => {
      val date = t.dateCompleted.getOrElse(t.dateStarted)
      val note = t.dateCompleted.map(_ => "Finished treatment").getOrElse("Started treatment")
      Activity(date, number, note)
    }

    def allActivity(): List[Activity] = {
      val notesActivity: List[Activity] = notes.map(_.asActivity)
      val medicamentsActivity: List[Activity] = medicaments.map(_.asActivity)
      val nextVisitsActivity: List[Activity] = nextVisits.map(_.asActivity)
      val treatmentsActivity: List[Activity] = treatments.map(_.asActivity())
      notesActivity ++ medicamentsActivity ++ nextVisitsActivity ++ treatmentsActivity
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
  case class Treatment(dateStarted: ZonedDateTime, dateCompleted: Option[ZonedDateTime] = None, info: String)
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