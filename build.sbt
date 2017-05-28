name := "granger"

version := "1.0"

scalaVersion := "2.12.2"

organization := "org.vaslabs"

val akkaVersion = "2.4.17"


libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-core" % "1.11.105",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.105",
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-tck" % akkaVersion % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "org.scalatest" %% "scalatest" % "2.1.7" % "test",
  "commons-io" % "commons-io" % "2.4" % "test",
  "org.hdrhistogram" % "HdrHistogram" % "2.1.8" % "test",
  "io.findify" %% "s3mock" % "0.1.10" % "test"
)