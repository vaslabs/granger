package org.vaslabs.granger.repo

import java.io.{File, PrintWriter}

import cats.syntax.either._
import org.eclipse.jgit.api.Git
import collection.JavaConverters._

/**
  * Created by vnicolaou on 04/06/17.
  */
package object git {
  def getFile(snapshotFile: String)(implicit git: Git): Either[WriteError, File] = {
    Either.catchNonFatal(new File(s"${git.getRepository.getDirectory.getAbsolutePath}/${snapshotFile}"))
      .leftMap(t => WriteError(t.getMessage))
  }

  def getWriter(file: File): Either[WriteError, PrintWriter] = {
    Either.catchNonFatal(new PrintWriter(file))
      .leftMap(t => WriteError(t.getMessage))
  }

  def write(payload: String, writer: PrintWriter): Either[WriteError, PrintWriter] = {
    Either.catchNonFatal(writer.write(payload)).leftMap(t => WriteError(t.getMessage)).map(_ => writer)
  }

  def commitChange(file: File, message: String)(implicit git: Git): Either[CommitError, File] = {
    import git._
    Either.catchNonFatal({
      val addCommand = add()
      add.addFilepattern(file.getName).call()
      val commitCommand = commit()
      commitCommand.setMessage(message).call()
      push().call()
    }).leftMap(t => CommitError(t.getMessage))
      .map(_.asScala).map(_ => file)
  }

  def saveTo(snapshotFile: String, payload: String, commitMessage: String)(implicit git: Git): Either[IOError, File] = {
    val fileEither = getFile(snapshotFile)
    val writerEither = fileEither.flatMap(getWriter(_))
    val writer = for {
      file <- fileEither
      writer <- writerEither
      writerAfterWrite <- write(payload, writer)
    } yield writer

    writerEither.foreach(_.close())
    fileEither.flatMap(commitChange(_, commitMessage))
  }
}
