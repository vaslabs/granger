package org.vaslabs.granger.github

import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source
import io.circe.parser.parse
import org.vaslabs.granger.github.releases.Release

/**
  * Created by vnicolaou on 01/08/17.
  */
class GithubSpec extends FlatSpec with Matchers{

  "the releases github json content" should "be parseable" in {
    val jsonContent = Source.fromResource("github_response.json").mkString
    val json = parse(jsonContent)
    json should matchPattern {
      case Right(_) =>
    }
    val releases = json.flatMap(
      _.as[List[Release]]
    )
    releases should matchPattern {
      case Right(_) =>
    }
  }
}
