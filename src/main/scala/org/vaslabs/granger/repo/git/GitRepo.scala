package org.vaslabs.granger.repo.git

import java.io.File

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import io.circe.{KeyDecoder, KeyEncoder, parser}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.vaslabs.granger.comms.api.model.RemoteRepo
import org.vaslabs.granger.model.{Patient, PatientId}
import org.vaslabs.granger.repo.{IOError, NotReady, Repo}
import io.circe.syntax._
import io.circe.generic.auto._
import org.vaslabs.granger.model.json._
import scala.io.Source
import scala.util.Try

/**
  * Created by vnicolaou on 29/06/17.
  */
class GitRepo(dbLocation: File, snapshotFile: String)(implicit gitApi: Git) extends Repo[Map[PatientId, Patient]]{
  implicit val keyEncoder: KeyEncoder[PatientId] = KeyEncoder[Long].contramap(_.id)
  implicit val keyDecoder: KeyDecoder[PatientId] = KeyDecoder[Long].map(PatientId(_))
  private type State = Map[PatientId, Patient]

  implicit val payloadEncoder: PayloadEncoder[State] = new PayloadEncoder[State] {
    override def encode(a: State): String = a.asJson.noSpaces
  }

  private def setUpRemote(remoteRepo: RemoteRepo): StatusCode = {
    Try {
      val remoteAddCommand = gitApi.remoteAdd()
      remoteAddCommand.setName("origin")
      remoteAddCommand.setUri(new URIish(remoteRepo.uri))
      remoteAddCommand.call()
      val file = new File(s"${dbLocation.getAbsolutePath}/$snapshotFile")
      file.createNewFile()
      save("Empty db file", Map.empty)
    }.map(_ => StatusCodes.Created)
      .getOrElse(StatusCodes.InternalServerError)
  }

  override def push(): Unit = gitApi.push().call()

  override def save(message: String, a: State): Either[IOError, File] =
    saveTo(snapshotFile, dbLocation, a, message)

  override def getState(): Either[NotReady, State] = {
    getFile(snapshotFile, dbLocation).map(file => {
      Source.fromFile(file).mkString
    }).flatMap(parser.parse(_))
      .flatMap(_.as[State])
        .flatMap(Right(_))
      .left.flatMap(_ => Left(NotReady("Error reading repo")))
  }

  override val setUp: (Any) => StatusCode = (a: Any) => a match {
    case rr: RemoteRepo =>
      setUpRemote(rr)
  }
}
