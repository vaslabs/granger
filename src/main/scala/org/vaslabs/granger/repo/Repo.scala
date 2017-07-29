package org.vaslabs.granger.repo

import java.io.File

import akka.http.scaladsl.model.StatusCode

/**
  * Created by vnicolaou on 29/06/17.
  */
trait Repo[A] {
  def saveNew(): Unit

  def getState(): Either[RepoErrorState, A]

  def save(message: String, a: A): Either[IOError, File]

  def push(): Unit

  val setUp: Any => StatusCode

}
