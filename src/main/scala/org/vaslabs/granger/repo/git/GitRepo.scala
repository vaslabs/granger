package org.vaslabs.granger.repo.git

import java.io.File

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import io.circe.{Decoder, Encoder, parser}
import io.circe.syntax._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.vaslabs.granger.comms.api.model.RemoteRepo
import org.vaslabs.granger.repo._
import org.vaslabs.granger.v2json._

import scala.io.Source
import scala.util.Try
import cats.syntax.either._
/**
  * Created by vnicolaou on 29/06/17.
  */
class GitRepo[A](dbLocation: File, snapshotFile: String)(
    implicit gitApi: Git, emptyProvider: EmptyProvider[A],
    encoder: Encoder[A], decoder: Decoder[A])
      extends Repo[A]
{

  implicit val payloadEncoder: PayloadEncoder[A] =
    (a: A) => a.asJson.noSpaces

  private[this] def saveEmptyFile() = {
    val file = new File(s"${dbLocation.getAbsolutePath}/$snapshotFile")
    file.createNewFile()
    save("Empty db file", emptyProvider.empty)
  }

  private def setUpRemote(remoteRepo: RemoteRepo): StatusCode = Try {
    val remoteAddCommand = gitApi.remoteAdd()
    remoteAddCommand.setName("origin")
    remoteAddCommand.setUri(new URIish(remoteRepo.uri))
    remoteAddCommand.call()
    Either.catchNonFatal {
      gitApi.pull().setRemote("origin").setRemoteBranchName("master").call()
    }.leftMap(_ => saveEmptyFile)
  }.map(_ => StatusCodes.Created)
    .getOrElse(StatusCodes.InternalServerError)

  override def push(): Unit = gitApi.push().call()

  override def save(message: String,
                    a: A): Either[IOError, File] =
    saveTo(snapshotFile, dbLocation, a, message)

  override def getState(): Either[RepoErrorState, A] = {
    getFile(snapshotFile, dbLocation)
      .map(file => {
        Source.fromFile(file).mkString
      })
      .flatMap(
        jsonString =>
          parser
            .parse(jsonString)
            .flatMap(_.as[A])
            .left
            .map(error => UnparseableSchema(error.getMessage))
      )
  }

  override val setUp: (Any) => StatusCode = (a: Any) =>
    a match {
      case rr: RemoteRepo =>
        setUpRemote(rr)
  }

  override def saveNew(): Unit = {
    val file = new File(s"${dbLocation.getAbsolutePath}/$snapshotFile")
    file.createNewFile()
    save(s"Empty db file ${snapshotFile}", emptyProvider.empty)
  }
}
