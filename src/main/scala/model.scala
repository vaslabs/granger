import java.time.ZonedDateTime

/**
 * Created by vnicolaou on 28/05/17.
 */
object model {


  case class Patient(name: String, dateOfBirth: ZonedDateTime, dentalChart: DentalChart)

  case class DentalChart(teeth: List[Tooth])


  trait Root {
    val size: Int
    val thickness: String
    val name: String
  }

  case class ToothDetails(roots: List[Root], medicament: String, nextVisit: String, notes: String)


  trait Tooth {
    val number: Int
    val details: ToothDetails
  }

}
