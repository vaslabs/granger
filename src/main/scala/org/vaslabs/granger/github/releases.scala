package org.vaslabs.granger.github

import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import cats.syntax.either._
/**
  * Created by vnicolaou on 01/08/17.
  */
object releases {
  final class ReleaseTag(val value: String) extends AnyVal {
    private def release_parts = value.split('.')
    def major = release_parts.apply(0).toInt
    def minor = release_parts.apply(1).toInt
    def isValid: Either[String, Double] = Either.catchNonFatal(value.toDouble).leftMap(_ => value)
  }

  object ReleaseTag {
    @inline def apply(value: String) = new ReleaseTag(value)

    implicit val ordering: Ordering[ReleaseTag] = (r1, r2) => {
      (r1.major*10 + r1.minor) - (r2.major*10 + r2.minor)
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
  }
}
