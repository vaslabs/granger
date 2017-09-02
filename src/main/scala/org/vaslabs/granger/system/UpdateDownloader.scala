package org.vaslabs.granger.system

import java.io._
import java.net.{HttpURLConnection, URL, URLConnection}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern._
import net.lingala.zip4j.core.ZipFile
import org.dbpedia.extraction.dump.download.Downloader
import org.dbpedia.extraction.util.IOUtils.copy
import org.vaslabs.granger.github.releases.{Asset, Release, ReleaseTag}

import scala.concurrent.{ExecutionContext, Future}

class UpdateDownloader private(currentRelease: ReleaseTag, supervisor: ActorRef, baseDir: File) extends Actor with ActorLogging {
  import UpdateDownloader.{ValidReleases, UpdateCompleted}
  import context.dispatcher

  override def receive: Receive = {
    case ValidReleases(releases) =>
      val releasesAhead = releases.filter(_.tag_name > currentRelease).sorted
      releasesAhead.headOption.map(
       _.assets.headOption.map(self ! _)
      )
    case Asset(artifactUrl) =>
      log.info("Update to {}", artifactUrl)
      GrangerDownloader(new URL(artifactUrl), baseDir.getAbsolutePath).map(_ => UpdateCompleted) pipeTo supervisor
  }
}

object UpdateDownloader {
  case class ValidReleases(validReleases: List[Release])
  case object UpdateCompleted

  def baseDir()(implicit baseDirProvider: BaseDirProvider): File = {
    baseDirProvider.baseDir()
  }

  def props(currentRelease: ReleaseTag, updater: ActorRef)(implicit baseDirProvider: BaseDirProvider): Props = {
    Props(new UpdateDownloader(currentRelease, updater, baseDir()))
  }
}

trait FileDownloader extends Downloader
{
 def downloadTo(url : URL, dir : File) : File = {
    val file = new File(dir, targetName(url))
    downloadFile(url, file)
    file
  }

  def downloadFile(url : URL, file : File) : Unit = {
    val conn = url.openConnection
    try {
      downloadFile(conn, file)
    } finally conn match {
      case conn: HttpURLConnection => conn.disconnect
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

final class GrangerDownloader() extends FileDownloader {
  def download(url: URL, baseDir: String)(implicit executionContext: ExecutionContext): Future[File] = {
    Future {
      val file = new File(s"${sys.env.get("HOME").get}/granger.zip")
      downloadFile(url, file)
      val zipFile = new ZipFile(file)
      zipFile.extractAll(baseDir)
      new File(s"${baseDir}/granger")
    }
  }

  override def targetName(url: URL): String = ???
}

object GrangerDownloader {
  def apply(url: URL, baseDir: String)(implicit executionContext: ExecutionContext): Future[File] = {
    val downlaoder = new GrangerDownloader()
    downlaoder.download(url, baseDir)
  }
}