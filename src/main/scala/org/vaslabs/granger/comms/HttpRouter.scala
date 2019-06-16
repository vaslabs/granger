package org.vaslabs.granger.comms

import java.time.{ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import org.vaslabs.granger.comms.api.model.{AddToothInformationRequest, RemoteRepo}
import org.vaslabs.granger.modeltreatments.TreatmentCategory
import org.vaslabs.granger.modelv2.{Patient, PatientId}
import org.vaslabs.granger.spa.StaticResources
import org.vaslabs.granger.v2json._

import scala.concurrent.Future

import UserApi._

/**
  * Created by vnicolaou on 28/05/17.
  */
trait HttpRouter extends FailFastCirceSupport with StaticResources {
  this: GrangerApi[Future] =>

  private[this] def treatments(): Route =
    pathPrefix("treatment") {
      pathSuffix("start") {
        post {
          entity(as[UserApi.StartTreatment]) { rq =>
            complete(startNewTreatment(rq))
          }
        }
      }
    } ~ pathSuffix("finish") {
      post {
        entity(as[FinishTreatment]) { rq =>
          complete(finishTreatment(rq))
        }
      }
    } ~ pathSuffix("delete") {
      post {
        entity(as[DeleteTreatment]) { rq =>
          complete(deleteTreatment(rq))
        }
      }
    } ~ pathSuffix("notifications") {
      post {
        entity(as[ModifyReminder]) { rq =>
          complete(modifyReminder(rq))
        }
      }
    } ~ path("treatment" / "notifications" / ZonedDateTimeMatcher) { time =>
      get {
        complete(treatmentNotifications(time))
      }
    } ~ path ("treatment" / "notification" / ZonedDateTimeMatcher) { time =>
      parameters('patientId.as[Long]) { id =>
        delete {
          complete(deleteReminder(PatientId(id), time))
        }
      }
    }

  private[this] def defineApi(implicit system: ActorSystem, materializer: ActorMaterializer): Route = {

    path("api") {
      get {
        complete(retrieveAllPatients())
      } ~
        post {
          entity(as[Patient]) { patient =>
            complete(addPatient(patient))
          }
        }
    } ~
      pathPrefix("api" / "latestActivity" / IntNumber) { id => {
        pathEnd {
          get {
            complete(getLatestActivity(PatientId(id)))
          }
        }
      }
      } ~
      path("update") {
        post {
          entity(as[AddToothInformationRequest]) { rq =>
            complete(addToothInfo(rq))
          }
        }
      } ~
      path("pub_key") {
        get {
          complete(getPublicKey())
        }
      } ~ path("init") {
      entity(as[RemoteRepo]) { repo =>
        complete(initGitRepo(repo))
      }
    } ~ treatments() ~ path("api" / "remember") {
      get {
        complete(rememberedData())
      }
    } ~ path("patient" / LongNumber) { patientId =>
      delete {
        complete(deletePatient(PatientId(patientId)))
      }
    } ~ path("allreminders" / LongNumber) { id =>
      get {
        complete(allReminders(PatientId(id)))
      }
    } ~ path("patient" / LongNumber / "images") { patientId =>
        put {
            complete(StatusCodes.Accepted -> storeImage(PatientId(patientId)))
        } ~ get {
          complete(getImages(PatientId(patientId)))
        }
    }

  }

  private[this] def exceptionHandler(log: LoggingAdapter) = ExceptionHandler {
    case exception: Exception => complete(HttpResponse(InternalServerError, entity = exception.getMessage))
  }

  private[this] def ZonedDateTimeMatcher: PathMatcher1[ZonedDateTime] =
    Segment.flatMap { value: String =>
      Some(ZonedDateTime.parse(value, DateTimeFormatter.ISO_ZONED_DATE_TIME))
    }

  private[this] def rejectionHandler(log: LoggingAdapter) =
    RejectionHandler
      .newBuilder()
      .handle {
        case ValidationRejection(msg, cause) =>
          complete(HttpResponse(BadRequest, entity = msg))
      }
      .result()

  def routes(implicit system: ActorSystem, materializer: ActorMaterializer): Route =
    logRequest("HTTPRequest") {
      logResult("HTTPResponse") {
        handleExceptions(exceptionHandler(system.log)) {
          handleRejections(rejectionHandler(system.log)) {
            defineApi
          }
        }
      }
    } ~ staticResources

}

object UserApi {

  case class StartTreatment(patientId: PatientId, toothId: Int, category: TreatmentCategory)

  case class DeleteTreatment(patientId: PatientId, toothId: Int, createdOn: ZonedDateTime) {
    require(createdOn.getOffset.equals(ZoneOffset.UTC))
  }

  case class FinishTreatment(patientId: PatientId, toothId: Int, finishedOn: ZonedDateTime) {
    require(finishedOn.getOffset.equals(ZoneOffset.UTC))
  }

  case class ModifyReminder(reminderTimestamp: ZonedDateTime, snoozeTo: ZonedDateTime, patientId: PatientId)

}
