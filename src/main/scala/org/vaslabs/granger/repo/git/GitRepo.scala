package org.vaslabs.granger.repo.git

import java.io.File

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import io.circe.parser
import io.circe.syntax._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.vaslabs.granger.comms.api.model.RemoteRepo
import org.vaslabs.granger.modelv2.{Patient, PatientId}
import org.vaslabs.granger.repo._
import org.vaslabs.granger.v2json._

import scala.io.Source
import scala.util.Try

/**
  * Created by vnicolaou on 29/06/17.
  */
class GitRepo(dbLocation: File, snapshotFile: String)(implicit gitApi: Git)
    extends Repo[Map[PatientId, Patient]] {

  implicit val payloadEncoder: PayloadEncoder[Map[PatientId, Patient]] =
    (a: Map[PatientId, Patient]) => a.asJson.noSpaces

  private def setUpRemote(remoteRepo: RemoteRepo): StatusCode = {
    Try {
      val remoteAddCommand = gitApi.remoteAdd()
      remoteAddCommand.setName("origin")
      remoteAddCommand.setUri(new URIish(remoteRepo.uri))
      val file = new File(s"${dbLocation.getAbsolutePath}/$snapshotFile")
      file.createNewFile()
      remoteAddCommand.call()
      save("Empty db file", Map.empty)
    }.map(_ => StatusCodes.Created)
      .getOrElse(StatusCodes.InternalServerError)
  }

  override def push(): Unit = gitApi.push().call()

  override def save(message: String,
                    a: Map[PatientId, Patient]): Either[IOError, File] =
    saveTo(snapshotFile, dbLocation, a, message)

  override def getState(): Either[RepoErrorState, Map[PatientId, Patient]] = {
    getFile(snapshotFile, dbLocation)
      .map(file => {
        Source.fromFile(file).mkString
      })
      .flatMap(
        jsonString =>
          parser
            .parse(jsonString)
            .flatMap(_.as[Map[PatientId, Patient]])
            .left
            .map(error => UnparseableSchema(error.getMessage))
      )
  }

  override val setUp: (Any) => StatusCode = (a: Any) =>
    a match {
      case rr: RemoteRepo =>
        setUpRemote(rr)
  }
}
