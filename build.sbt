name := "granger"

version := "1.0"

scalaVersion := "2.12.2"

organization := "org.vaslabs"

val akkaVersion = "2.4.17"
val circeVersion = "0.8.0"


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % "10.0.7",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "commons-io" % "commons-io" % "2.4" % "test",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-java8" % circeVersion,
  "de.heikoseeberger" %% "akka-http-circe" % "1.16.0",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.7.0.201704051617-r",
  "com.github.pureconfig" %% "pureconfig" % "0.7.2"
)