package org.vaslabs.granger.comms

import akka.event.LoggingAdapter
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import StatusCodes._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import org.vaslabs.granger.model.json._
import org.vaslabs.granger.repo.GrangerRepo

import scala.concurrent.Future

/**
 * Created by vnicolaou on 28/05/17.
 */
trait HttpRouter extends FailFastCirceSupport{ this: GrangerRepo[Future] =>

  private[this] def defineApi(implicit system: ActorSystem,
                              materializer: ActorMaterializer): Route = {
    path("api") {
      get {
        complete(retrieveAllPatients)
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
    }

}
