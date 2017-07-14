name := "granger"

version := "1.0"

scalaVersion := "2.12.2"

organization := "org.vaslabs"

val akkaVersion = "2.5.2"
val circeVersion = "0.8.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % "10.0.9",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.0.9" % Test,
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

enablePlugins(sbtdocker.DockerPlugin)
Revolver.settings
enablePlugins(JavaServerAppPackaging)
enablePlugins(DockerComposePlugin)
enablePlugins(UniversalPlugin)
parallelExecution in ThisBuild := false

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
    workDir("/home/granger")
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
