package org.vaslabs.granger.comms

import akka.event.LoggingAdapter
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import StatusCodes._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.vaslabs.granger.model.Patient
import org.vaslabs.granger.repo.GrangerRepo
import org.vaslabs.granger.spa.StaticResources
import io.circe.generic.auto._
import org.vaslabs.granger.comms.api.model.AddToothInformationRequest
import org.vaslabs.granger.model.json._

import scala.concurrent.Future

/**
 * Created by vnicolaou on 28/05/17.
 */
trait HttpRouter extends FailFastCirceSupport with StaticResources { this: GrangerApi[Future] =>

  private[this] def defineApi(implicit system: ActorSystem,
                              materializer: ActorMaterializer): Route = {

    path("api") {
      get {
        complete(retrieveAllPatients)
      } ~
      post {
        entity(as[Patient]) {
          patient =>
            complete(addPatient(patient))
        }
      }
    } ~
    path("update") {
      post {
        entity(as[AddToothInformationRequest]) {
          rq => complete(addToothInfo(rq))
        }
      }
    }
  }

  private[this] def exceptionHandler(log: LoggingAdapter) = ExceptionHandler {
    case exception: Exception => complete(HttpResponse(InternalServerError, entity = exception.getMessage))

  }

  private[this] def rejectionHandler(log: LoggingAdapter) =
    RejectionHandler.newBuilder().handle {
      case ValidationRejection(msg, cause) =>
        complete(HttpResponse(BadRequest, entity = msg))
    } result ()

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
