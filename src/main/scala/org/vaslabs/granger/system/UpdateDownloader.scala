package org.vaslabs.granger.system

import java.io.{File, FileOutputStream, InputStream, OutputStream}
import java.net.{HttpURLConnection, URL, URLConnection}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.dbpedia.extraction.dump.download.Downloader
import org.vaslabs.granger.github.releases.{Asset, Release, ReleaseTag}
import akka.pattern._
import org.dbpedia.extraction.util.IOUtils.copy

import scala.concurrent.{ExecutionContext, Future}

class UpdateDownloader private(currentRelease: ReleaseTag, supervisor: ActorRef) extends Actor with ActorLogging with FileDownloader {
  import UpdateDownloader.ValidReleases

  import context.dispatcher
  override def receive: Receive = {
    case ValidReleases(releases) =>
      val releasesAhead = releases.filter(_.tag_name.greaterThan(currentRelease)).sorted
      releasesAhead.headOption.map(
       _.assets.headOption.map(self ! _)
      )
    case Asset(artifactUrl) =>
      log.info("Update to {}", artifactUrl)
      GrangerDownloader(new URL(artifactUrl)) pipeTo supervisor
  }
}

object UpdateDownloader {
  case class ValidReleases(validReleases: List[Release])

  def props(currentRelease: ReleaseTag, updater: ActorRef): Props = {
    Props(new UpdateDownloader(currentRelease, updater))
  }
}

trait FileDownloader extends Downloader
{
  /**
    * Use "index.html" if URL ends with "/"
    */
  def targetName(url : URL) : String = {
    val path = url.getPath
    var part = path.substring(path.lastIndexOf('/') + 1)
    if (part.nonEmpty) part else "index.html"
  }

  /**
    * Download file from URL to directory.
    */
  def downloadTo(url : URL, dir : File) : File = {
    val file = new File(dir, targetName(url))
    downloadFile(url, file)
    file
  }

  /**
    * Download file from URL to given target file.
    */
  def downloadFile(url : URL, file : File) : Unit = {
    val conn = url.openConnection
    try {
      downloadFile(conn, file)
    } finally conn match {
      // http://dumps.wikimedia.org/ seems to kick us out if we don't disconnect.
      case conn: HttpURLConnection => conn.disconnect
      // But only disconnect if it's a http connection. Can't do this with file:// URLs.
      case _ =>
    }
  }

  /**
    * Download file from URL to given target file.
    */
  protected def downloadFile(conn: URLConnection, file : File): Unit = {
    val in = inputStream(conn)
    try
    {
      val out = outputStream(file)
      try
      {
        copy(in, out)
      }
      finally out.close
    }
    finally in.close
  }

  /**
    * Get input stream. Mixins may decorate the stream or open a different stream.
    */
  protected def inputStream(conn: URLConnection) : InputStream = conn.getInputStream

  /**
    * Get output stream. Mixins may decorate the stream or open a different stream.
    */
  protected def outputStream(file: File) : OutputStream = new FileOutputStream(file)

}

final class GrangerDownloader() extends FileDownloader() {
  def download(url: URL)(implicit executionContext: ExecutionContext): Future[File] = {
    Future {
      val file = new File(s"${sys.env.get("HOME").get}/granger.zip")
      downloadFile(url, file)
      file
    }
  }

}

object GrangerDownloader {
  def apply(url: URL)(implicit executionContext: ExecutionContext): Future[File] = {
    val downlaoder = new GrangerDownloader()
    downlaoder.download(url)
  }
}