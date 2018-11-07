logLevel := Level.Warn

addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.5.0")
addSbtPlugin("com.typesafe.sbt" %% "sbt-native-packager" % "1.3.2")
addSbtPlugin("com.tapad" % "sbt-docker-compose" % "1.0.34")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")
addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "1.3.15")