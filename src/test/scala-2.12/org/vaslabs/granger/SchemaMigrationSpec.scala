package org.vaslabs.granger

import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source
import io.circe.parser._
import org.vaslabs.granger.model.{Patient, PatientId}
import io.circe.generic.auto._
import org.vaslabs.granger.modelv2.RootCanalTreatment
/**
  * Created by vnicolaou on 02/07/17.
  */
class SchemaMigrationSpec extends FlatSpec with Matchers {

  "schema migration from model" should "transform to modelv2" in {
    val patients = parse(Source.fromResource("v1Schema.json").mkString).flatMap(_.as[Map[PatientId, Patient]]).toOption.get
    val migration = patients.mapValues(modelv2.migrate(_))
    migration.size shouldBe patients.size
    migration.get(PatientId(1)).get.dentalChart.teeth.find(_.number == 16).get.treatments.head.category shouldBe RootCanalTreatment()
  }

  "new schema" should "be readable" in {
    val patients = parse(Source.fromResource("newSchema.json").mkString).flatMap(_.as[Map[modelv2.PatientId, modelv2.Patient]])
    patients should matchPattern {
      case Right(s) =>
    }
  }

}
