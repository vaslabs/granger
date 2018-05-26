name := "granger"

version := "1.0"

scalaVersion := "2.12.6"

organization := "org.vaslabs"

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)


val akkaVersion = "2.5.12"
val circeVersion = "0.9.3"
val monocleVersion = "1.5.1-cats"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % "10.1.1",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.1.1" % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "commons-io" % "commons-io" % "2.4" % "test",
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-java8" % circeVersion,
  "de.heikoseeberger" %% "akka-http-circe" % "1.20.1",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.7.0.201704051617-r",
  "com.github.pureconfig" %% "pureconfig" % "0.9.1",
  "de.knutwalker" %% "akka-http-circe" % "3.5.0",
  "de.knutwalker" %% "akka-http-json" % "3.5.0",
  "org.typelevel" %% "cats-effect" % "1.0.0-RC",
  "com.github.julien-truffaut" %%  "monocle-core"  % monocleVersion,
  "com.github.julien-truffaut" %%  "monocle-macro" % monocleVersion,
  "com.github.julien-truffaut" %%  "monocle-law"   % monocleVersion % "test"

)

enablePlugins(sbtdocker.DockerPlugin)
Revolver.settings
enablePlugins(JavaServerAppPackaging)
enablePlugins(DockerComposePlugin)
enablePlugins(UniversalPlugin)
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

name in UniversalDocs <<= name in Universal

name in UniversalSrc <<= name in Universal

packageName in Universal := packageName.value

