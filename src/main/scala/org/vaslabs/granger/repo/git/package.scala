package org.vaslabs.granger.repo

import java.io.{File, PrintWriter}

import cats.syntax.either._
import org.eclipse.jgit.api.Git
import collection.JavaConverters._

/**
  * Created by vnicolaou on 04/06/17.
  */
package object git {
  def getFile(snapshotFile: String, directory: File)(
      implicit git: Git): Either[RepoErrorState, File] = {
    Either
      .catchNonFatal(new File(s"${directory.getAbsolutePath}/${snapshotFile}"))
      .leftMap(t => UnkownState(t.getMessage))
      .flatMap(file =>
        file match {
          case file: File if file.exists() => Right(file)
          case _ => Left(EmptyRepo)
      })
  }

  def getWriter(file: File): Either[WriteError, PrintWriter] = {
    Either
      .catchNonFatal(new PrintWriter(file))
      .leftMap(t => WriteError(t.getMessage))
  }

  def write(payload: String,
            writer: PrintWriter): Either[WriteError, PrintWriter] = {
    Either
      .catchNonFatal(writer.write(payload))
      .leftMap(t => WriteError(t.getMessage))
      .map(_ => writer)
  }

  def commitChange(file: File, message: String)(
      implicit git: Git): Either[CommitError, File] = {
    import git._
    Either
      .catchNonFatal({
        val addCommand = add()
        add.addFilepattern(file.getName).call()
        val commitCommand = commit()
        commitCommand.setMessage(message).call()
      })
      .leftMap(t => { println(t); CommitError(t.getMessage) })
      .map(_ => file)
  }

  def saveTo[A](snapshotFile: String,
                location: File,
                a: A,
                commitMessage: String)(
      implicit git: Git,
      payloadEncoder: PayloadEncoder[A]): Either[IOError, File] = {
    val fileEither = getFile(snapshotFile, location)
    val writerEither = fileEither.flatMap(getWriter(_))
    val writer = for {
      file <- fileEither
      writer <- writerEither
      writerAfterWrite <- write(payloadEncoder.encode(a), writer)
    } yield writer
    writerEither.left.foreach(println(_))
    writerEither.foreach(_.close())
    fileEither.left
      .map(e => WriteError("Failure to save data"))
      .flatMap(commitChange(_, commitMessage))
  }
}
