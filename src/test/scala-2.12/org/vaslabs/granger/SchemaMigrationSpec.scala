package org.vaslabs.granger

import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source
import io.circe.parser._
import org.vaslabs.granger.model.{Patient, PatientId}
import io.circe.generic.auto._
/**
  * Created by vnicolaou on 02/07/17.
  */
class SchemaMigrationSpec extends FlatSpec with Matchers {

  "schema migration from model" should "transform to modelv2" in {
    val patients = parse(Source.fromResource("v1Schema.json").mkString).flatMap(_.as[Map[PatientId, Patient]]).toOption.get
    patients.mapValues(modelv2.migrate(_)).size shouldBe patients.size
  }

}
