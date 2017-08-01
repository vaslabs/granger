package org.vaslabs.granger.system

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import de.knutwalker.akka.stream.support.CirceStreamSupport.decode
import org.vaslabs.granger.github.releases.{Release, ReleaseTag}
/**
  * Created by vnicolaou on 01/08/17.
  */
class UpdateChecker private(conf: UpdateConfig, updater: ActorRef) extends Actor with ActorLogging{
  import org.vaslabs.granger.system.UpdateChecker._

  import akka.pattern.pipe
  import context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  val http = Http(context.system)

  override def receive: Receive = {
    case CheckForUpdates =>
      http.singleRequest(HttpRequest(uri = conf.url))
        .pipeTo(self)
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      entity.dataBytes.via(decode[List[Release]]).map(
        releases => releases.filter(_.tag_name.validity.isRight).filterNot(_.prerelease)
      ).map(Updater.ValidReleases(_)).runWith(Sink.actorRef(updater, Done))
  }
}

object UpdateChecker {
  case object CheckForUpdates

  def props(updater: ActorRef): Props = {
    Props(new UpdateChecker(UpdateConfig(), updater))
  }
}


