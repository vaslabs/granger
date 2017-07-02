package org.vaslabs.granger

import java.io.File

import org.eclipse.jgit.api.Git
import org.vaslabs.granger.PatientManager.{LoadDataOutcome, MigrationFailure, MigrationSuccess}
import org.vaslabs.granger.repo.git.PayloadEncoder

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

/**
  * Created by vnicolaou on 02/07/17.
  */
object Migrations {
  import io.circe.syntax._
  implicit val payloadEncoder: PayloadEncoder[Map[modelv2.PatientId, modelv2.Patient]] = new PayloadEncoder[Map[modelv2.PatientId, modelv2.Patient]] {
    override def encode(a: Map[modelv2.PatientId, modelv2.Patient]): String = a.asJson.noSpaces
  }
  import io.circe.generic.auto._
  import org.vaslabs.granger.repo.git._
  import io.circe.parser._

  def attemptSchemaMigrationToV2(grangerConfig: GrangerConfig)(implicit git: Git, executionContext: ExecutionContext) =
      Future {
        getFile("patients.json", new File(grangerConfig.repoLocation)).map(
          file =>
            Source.fromFile(file).mkString
        ).flatMap(parse(_)).flatMap(_.as[Map[model.PatientId, model.Patient]]).map(
          oldState => oldState.values.map(modelv2.migrate(_))
        ).map(_.groupBy(_.patientId).mapValues(_.head))
          .map(state => {
            saveTo[Map[modelv2.PatientId, modelv2.Patient]]("patients.json", new File(grangerConfig.repoLocation), state, s"Migrated schema to modelv2")
          }
            .map(_ => MigrationSuccess).getOrElse(MigrationFailure)
          ).getOrElse(MigrationFailure)
  }

}
