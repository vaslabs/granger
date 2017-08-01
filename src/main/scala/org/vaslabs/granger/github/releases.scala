package org.vaslabs.granger.github

import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import cats.syntax.either._
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.TagName
/**
  * Created by vnicolaou on 01/08/17.
  */
object releases {
  final class ReleaseTag(val value: String) extends AnyVal {
    def greaterThan(other: ReleaseTag): Boolean = {
      (major*10 + minor) > (other.major*10 + other.minor)
    }

    private def release_parts = value.split('.')
    def major = release_parts.apply(0).toInt
    def minor = release_parts.apply(1).toInt
    def -(other: ReleaseTag) =  (major*10 + minor) - (other.major*10 + other.minor)
    def validity: Either[String, Double] = Either.catchNonFatal(value.toDouble).leftMap(_ => value)
  }

  object ReleaseTag {
    @inline def apply(value: String) = new ReleaseTag(value)

    implicit val ordering: Ordering[ReleaseTag] = (r1, r2) => {
      r1 - r2
    }

    implicit val encoder: Encoder[ReleaseTag] = Encoder[String].contramap(_.value)
    implicit val decoder: Decoder[ReleaseTag] = Decoder[String].map(ReleaseTag(_))
  }

  case class Asset(browser_download_url: String)

  case class Release(tag_name: ReleaseTag, published_at: ZonedDateTime, assets: List[Asset], prerelease: Boolean)

  object Release {

    import org.vaslabs.granger.v2json.{zonedDateTimeDecoder, zonedDateTimeEncoder}
    import io.circe.generic.semiauto._

    implicit val encoder: Encoder[Release] = deriveEncoder[Release]
    implicit val decoder: Decoder[Release] = deriveDecoder[Release]

    implicit val ordering: Ordering[Release] = (r1, r2) => {
      r1.tag_name - r2.tag_name
    }
  }
}
