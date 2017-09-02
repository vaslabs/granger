package org.vaslabs.granger.system

import java.net.URL

import org.scalatest.{AsyncFlatSpec, FlatSpec}
/**
  * Created by vnicolaou on 02/09/17.
  */
class GrangerDownloaderSpec extends AsyncFlatSpec{

  "given granger zip location it" should "download the zip file" in {
    val newRelease = GrangerDownloader(
      new URL("https://github.com/vaslabs/granger/releases/download/1.4/granger.zip"),
      s"${sys.env.get("HOME").get}/Desktop")
    newRelease.map(f => assert(f.exists() && f.isDirectory))

  }

}
