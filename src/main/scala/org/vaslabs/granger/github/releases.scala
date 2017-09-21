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
    def >(other: ReleaseTag): Boolean = {
      relNumber > other.relNumber
    }

    def relNumber: Double = value.toDouble
    def -(other: ReleaseTag) =  relNumber - other.relNumber
    def validity: Either[String, Double] = Either.catchNonFatal(value.toDouble).leftMap(_ => value)
  }

  object ReleaseTag {

    val CURRENT = apply("1.72")

    @inline def apply(value: String) = new ReleaseTag(value)

    implicit val ordering: Ordering[ReleaseTag] = (r1, r2) => {
      (r1 - r2).toInt
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
      (r1.tag_name - r2.tag_name).toInt
    }
  }
}
