package org.vaslabs.granger

import java.time.ZonedDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.vaslabs.granger.repo.git.GitRepo

import scala.concurrent.duration._
import org.vaslabs.granger.repo.GrangerRepo

import scala.concurrent.Future

/**
  * Created by vnicolaou on 01/07/17.
  */
object GitRepoPusher {
  def props(grangerRepo: GrangerRepo[Map[model.PatientId, model.Patient], Future])(implicit repo: GitRepo): Props =
    Props(new GitRepoPusher(grangerRepo))

  case object PushChanges

}
class GitRepoPusher private (grangerRepo: GrangerRepo[Map[model.PatientId, model.Patient], Future])(implicit repo: GitRepo) extends Actor with ActorLogging{

  import context.dispatcher
  import org.vaslabs.granger.GitRepoPusher._

  private[this] def schedulePushJob(): Unit = {
    context.system.scheduler.scheduleOnce(15 seconds, self, PushChanges)
  }
  override def receive: Receive = {
    case PushChanges =>
      schedulePushJob()
      context.become(scheduledJob)
  }

  private def scheduledJob: Receive = {
    case PushChanges =>
      grangerRepo.pushChanges().onComplete({
        job =>
          if (job.isSuccess) {
            log.info(s"Push to remote repository. Last push: ${ZonedDateTime.now()}")
            context.become(receive)
          }
          else {
            log.warning("Push to remote repository failed. Backups may become out of sync")
            schedulePushJob()
          }
      })
  }
}
