package org.vaslabs.granger

import java.time.ZonedDateTime

import akka.actor.{Actor, ActorLogging, Props}
import org.vaslabs.granger.modelv2.{Patient, PatientId}
import org.vaslabs.granger.repo.git.GitRepo

import scala.concurrent.duration._
import org.vaslabs.granger.repo.GrangerRepo

import scala.concurrent.Future

/**
  * Created by vnicolaou on 01/07/17.
  */
object GitRepoPusher {
  def props(grangerRepo: GrangerRepo[Map[PatientId, Patient], Future])(implicit repo: GitRepo): Props =
    Props(new GitRepoPusher(grangerRepo))

  case object PushChanges

}
class GitRepoPusher private (grangerRepo: GrangerRepo[Map[PatientId, Patient], Future])(implicit repo: GitRepo) extends Actor with ActorLogging{

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
        job => job.fold(
          t => {
            log.warning(s"Push to remote repository failed. Backups may become out of sync: ${t}")
            schedulePushJob()
          },
          _ => {
            log.info(s"Push to remote repository. Last push: ${ZonedDateTime.now()}")
            context.become(receive)
          }
        )
      })
  }
}
