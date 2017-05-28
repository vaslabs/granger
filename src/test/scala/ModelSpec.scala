import org.scalatest.{AsyncFlatSpec, FlatSpec, Matchers}
import org.vaslabs.granger.repo.mock.MockGrangerRepo
import io.circe.syntax._
import io.circe.generic.auto._
import org.vaslabs.granger.model.Patient
import org.vaslabs.granger.model.json._
/**
  * Created by vnicolaou on 28/05/17.
  */
class ModelSpec extends AsyncFlatSpec with Matchers{

  "model" should "have a json representation" in {
    MockGrangerRepo.retrieveAllPatients().map(
      _.asJson.as[List[Patient]] should matchPattern { case Right(_) => }
    )
  }
}
