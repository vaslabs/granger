package org.vaslabs.granger.spa


import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.io.Source
import akka.http.scaladsl.model.StatusCodes._

/**
  * Created by vnicolaou on 29/05/17.
  */
trait StaticResources {

  private[this] final val AssetsPath = "assets"
  private[this] final val PublicDir  = "public/"

  private[this] def getDefaultPage(): String = {
    Source.fromResource("index.html").getLines.mkString
  }

  private[this] def getStaticJsResource(resource: String): HttpResponse = {
    HttpResponse(OK, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, Source.fromResource(resource).getLines().mkString("\n")))
  }

  def staticResources: Route =
    pathEndOrSingleSlash {
      get {
        complete(HttpResponse(OK, entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, getDefaultPage())))
      }
    } ~ path("js" / "main.js") {
      get {
        complete(getStaticJsResource("app.js"))
      }
    }


}
