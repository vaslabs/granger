package org.vaslabs.granger.comms.api

import org.vaslabs.granger.model.{Root, ToothNote}

/**
  * Created by vnicolaou on 13/06/17.
  */
object model {

  case class RootRequest(toothNumber: Int, root: Root)
  case class ToothNoteRequest(toothNumber: Int, toothNote: ToothNote)
}
