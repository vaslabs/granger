package org.vaslabs.granger

import java.io.File
import java.util.UUID

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.IO
import org.eclipse.jgit.api.Git
import org.vaslabs.granger.modelv2.{PatientId, PatientImages}
import org.vaslabs.granger.repo.EmptyRepo
import org.vaslabs.granger.repo.git.{EmptyProvider, GitRepo}

object ImageStore {

  import v2json._
  import io.circe.generic.auto._
  implicit val emptyProvider: EmptyProvider[Map[PatientId, List[UUID]]] = () => Map.empty

  def behavior(grangerConfig: GrangerConfig, idGen: IO[UUID])(implicit git: Git): Behavior[Protocol] = Behaviors.setup { ctx =>
    val repo: GitRepo[Map[PatientId, List[UUID]]] =
      new GitRepo[Map[PatientId, List[UUID]]](new File(grangerConfig.repoLocation), "image_store.json")
    repo.getState() match {
      case Left(EmptyRepo) =>
        repo.saveNew()
    }
    Behaviors.receiveMessage {
      case StoreImage(patientId, _, replyTo) =>
        val id = idGen.unsafeRunSync()
        repo.getState() match {
          case Right(state) =>
            val patientImages = state.getOrElse(patientId, List.empty) :+ id
            val updatedState = state + (patientId -> patientImages)
            repo.save(s"Adding image to patient ${patientId.id}", updatedState)
          case _ =>
            ctx.log.error("Can't save images, database is not setup properly for image_store.json")
        }
        replyTo ! id
        Behaviors.same
      case FetchImages(patientId, replyTo) =>
        repo.getState() match {
          case Right(state) =>
            replyTo ! PatientImages(patientId, state.getOrElse(patientId, List.empty))
          case Left(_) =>
            ctx.log.warning("Database is not setup properly for image references")
            replyTo ! PatientImages(patientId, List.empty)
        }
        Behaviors.same
    }
  }

  sealed trait Protocol
  case class StoreImage(patientId: PatientId, payload: Array[Byte], replyTo: ActorRef[UUID]) extends Protocol

  case class FetchImages(patientId: modelv2.PatientId, replyTo: ActorRef[modelv2.PatientImages]) extends Protocol

}
