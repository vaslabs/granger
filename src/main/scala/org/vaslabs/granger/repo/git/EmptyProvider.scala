package org.vaslabs.granger.repo.git

/**
  * Created by vnicolaou on 29/07/17.
  */
trait EmptyProvider[A] {
  def empty(): A
}
