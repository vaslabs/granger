package org.vaslabs.granger

import akka.actor.{Actor, ActorLogging, Props}
import cats.effect.IO
import org.vaslabs.granger.modelv2.{Patient, PatientId}
import org.vaslabs.granger.repo.git.GitRepo

import scala.concurrent.duration._
import org.vaslabs.granger.repo.GrangerRepo

/**
  * Created by vnicolaou on 01/07/17.
  */
object GitRepoPusher {
  def props(grangerRepo: GrangerRepo[Map[PatientId, Patient], IO])(
      implicit repo: GitRepo[Map[PatientId, Patient]]): Props =
    Props(new GitRepoPusher(grangerRepo))

  case object PushChanges
  private case object DoPush
}
class GitRepoPusher private (grangerRepo: GrangerRepo[Map[PatientId, Patient], IO])(
    implicit repo: GitRepo[Map[PatientId, Patient]]) extends Actor with ActorLogging
{

  import context.dispatcher
  import org.vaslabs.granger.GitRepoPusher._

  private[this] def schedulePushJob(): Unit = {
    context.system.scheduler.scheduleOnce(15 seconds, self, DoPush)
  }
  override def receive: Receive = {
    case PushChanges =>
      schedulePushJob()
      context.become(scheduledJob)
  }

  private def scheduledJob: Receive = {
    case DoPush =>
      grangerRepo.pushChanges().map(_ => context.become(receive)).unsafeRunSync()
  }
}
