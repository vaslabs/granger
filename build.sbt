import sbt._

name := "granger"

version := "1.0"

scalaVersion := "2.12.8"

organization := "org.vaslabs"

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)


val akkaVersion = "2.6.0-M3"
val akkaHttpVersion = "10.1.8"
val circeVersion = "0.9.3"
val monocleVersion = "1.5.1-cats"

lazy val akka = Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
)
libraryDependencies ++= akka ++ Seq(
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "commons-io" % "commons-io" % "2.4" % Test,
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-java8" % circeVersion,
  "de.heikoseeberger" %% "akka-http-circe" % "1.21.0",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "5.1.1.201809181055-r",
  "com.github.pureconfig" %% "pureconfig" % "0.11.1",
  "de.knutwalker" %% "akka-http-circe" % "3.5.0",
  "de.knutwalker" %% "akka-http-json" % "3.5.0",
  "org.typelevel" %% "cats-effect" % "1.3.1",
  "com.github.julien-truffaut" %%  "monocle-core"  % monocleVersion,
  "com.github.julien-truffaut" %%  "monocle-macro" % monocleVersion,
  "com.github.julien-truffaut" %%  "monocle-law"   % monocleVersion % Test

)

enablePlugins(sbtdocker.DockerPlugin)
enablePlugins(JavaServerAppPackaging)
enablePlugins(DockerComposePlugin)
enablePlugins(UniversalPlugin)
enablePlugins(WindowsPlugin)
lazy val windowsInstallerSettings = {
  maintainer := "Vasilis Nicolaou <vaslabsco@gmail.com>"
  packageSummary := "Granger"
  packageDescription := """A patient management system for dentists for Root Canal Treatment"""

  // wix build information
  wixProductId := "*"
  wixProductUpgradeId := "*"
  name in Windows := "Granger"
}

parallelExecution in ThisBuild := false

import NativePackagerHelper._
mappings in Universal ++= directory(s"${baseDirectory.value}/static")

dockerfile in docker := {
  val appDir: File = stage.value
  val targetDir = "/app"

  new Dockerfile {
    from("java")
    entryPoint(s"$targetDir/bin/${executableScriptName.value}")
    copy(appDir, targetDir)
    runRaw(
      "apt-get update && apt-get install -y zip unzip python-fontforge openssh-client git && git config --global user.name \"granger\" && git config --global user.email \"granger@vaslabs.org\"")
    runRaw("useradd -ms /bin/bash granger")
    runRaw("echo StrictHostKeyChecking no >>/etc/ssh/ssh_config")
    user("granger")
    workDir("/app/")
    runRaw(
      "mkdir -p $HOME/.ssh && cd /home/granger/.ssh && ssh-keygen -q -t rsa -N '' -f id_rsa")
  }
}
buildOptions in docker := BuildOptions(cache = false)

dockerImageCreationTask := (publishLocal in Docker).value

name in Universal := name.value


packageName in Universal := packageName.value

addCommandAlias("reportTestCov", ";coverageReport; coverageAggregate; codacyCoverage")