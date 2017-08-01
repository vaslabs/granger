package org.vaslabs.granger.system

import java.io.File

/**
  * Created by vnicolaou on 02/08/17.
  */
trait Downloader {

  def download(assetUrl: String): Either[String, File]

}
