package org.vaslabs.granger.spa


import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directives, Route}

import scala.io.Source
import akka.http.scaladsl.model.StatusCodes._
import better.files.File
/**
  * Created by vnicolaou on 29/05/17.
  */
trait StaticResources {


  private[this] def getDefaultPage(): String = {
    Source.fromResource("index.html").getLines.mkString
  }

  private[this] def getStaticJsResource(resource: String): HttpResponse = {
    HttpResponse(OK, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, Source.fromResource(resource).getLines().mkString("\n")))
  }

  private[this] def getStaticCssResource(resource: String): HttpResponse = {
    HttpResponse(OK, entity = HttpEntity(ContentType(MediaTypes.`text/css`, HttpCharsets.`UTF-8`), Source.fromResource(resource).getLines().mkString("\n")))
  }

  private[this] def getStaticFontResource(resource: String): HttpResponse = {
    val contentTypeHeader: List[HttpHeader] = HttpHeader.parse("content-type", "application/font-woff2") match {
      case ok: ParsingResult.Ok =>
        List(ok.header)
      case _ => List.empty
    }
    HttpResponse(OK, headers = contentTypeHeader, entity = HttpEntity(File.resource(resource).byteArray))
  }

  private val recognisedFonts: Set[String] =
    Set(
      "Roboto-Regular.woff2",
      "Roboto-Medium.woff2",
      "Roboto-Bold.woff2"
    )
  def staticResources: Route =
    pathEndOrSingleSlash {
      get {
        complete(HttpResponse(OK, entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, getDefaultPage())))
      }
    } ~ path("js" / "main.js") {
      get {
        complete(getStaticJsResource("app.js"))
      }
    } ~ path("js" / "angular.js") {
      get {
        complete(getStaticJsResource("js/angular.min.js"))
      }
    } ~ path("js" / "materialize.js") {
      complete(getStaticJsResource("js/materialize.min.js"))
    } ~
    path("css" / "materialize.css") {
      complete(getStaticCssResource("css/materialize.min.css"))
    } ~ path("js" / "jquery.js") {
      complete(getStaticJsResource("js/jquery-2.1.1.min.js"))
    } ~ path("fonts"/"roboto"/ Directives.RemainingPath ) {
      remainingPath =>
        get {
          val remainingPathString = remainingPath.toString
          if (recognisedFonts.contains(remainingPathString))
            complete(getStaticFontResource(s"fonts/${remainingPathString}"))
          else
            complete(NotFound)
        }
    }


}
