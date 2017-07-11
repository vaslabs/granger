package org.vaslabs.granger.spa

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directives, Route}

import scala.io.Source
import akka.http.scaladsl.model.StatusCodes._
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

  private[this] def getStaticFontResource(resource: Array[Byte]): HttpResponse = {
    val contentType = ContentType.parse("application/font-woff2").getOrElse(ContentTypes.NoContentType)
    HttpResponse(OK, entity = HttpEntity(contentType, resource))
  }


  private[this] def readBytes(resource: String): Array[Byte] = {
    val isr = getClass.getResource(resource).openStream()
    var array = new Array[Byte](8192)

    var bytesRead = isr.read(array)
    var totalBytesRead = bytesRead
    while (bytesRead > 0) {
      val addedBytes = new Array[Byte](8192)
      bytesRead = isr.read(addedBytes)
      totalBytesRead += bytesRead
      array = array ++ addedBytes
    }
    array = array.take(totalBytesRead + 1)
    array
  }

  private val recognisedFonts: Map[String, Array[Byte]] = Map(
    "Roboto-Regular.woff2" -> readBytes("/fonts/Roboto-Regular.woff2"),
    "Roboto-Medium.woff2" -> readBytes("/fonts/Roboto-Medium.woff2"),
    "Roboto-Bold.woff2" -> readBytes("/fonts/Roboto-Bold.woff2"),
    "material_icons.woff2" -> readBytes("/fonts/material_icons.woff2")
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
    } ~
      path("css" / "material_icons.css") {
        complete(getStaticCssResource("css/material_icons.css"))
    } ~ path("js" / "jquery.js") {
      complete(getStaticJsResource("js/jquery-2.1.1.min.js"))
    } ~ path("fonts"/"roboto"/ Directives.RemainingPath ) {
      remainingPath =>
        get {
          val remainingPathString = remainingPath.toString
          recognisedFonts.get(remainingPathString).map(
            font => complete(getStaticFontResource(font))
          ).getOrElse(complete(NotFound))
        }
    }


}
