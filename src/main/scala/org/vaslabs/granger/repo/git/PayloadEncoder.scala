package org.vaslabs.granger.repo.git

/**
  * Created by vnicolaou on 29/06/17.
  */
trait PayloadEncoder[A] {

  def encode(a: A): String

}
