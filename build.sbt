name := "mongo-elastic-connector"

version := "1.0"

scalaVersion := "2.12.6"

lazy val akkaVersion = "2.5.13"

//enablePlugins(DockerPlugin)

libraryDependencies ++= Seq(

  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.reactivemongo" %% "play2-reactivemongo" % "0.13.0-play26",
  "org.reactivemongo" %% "reactivemongo-akkastream" % "0.13.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0",
  "com.github.maricn" % "logback-slack-appender" % "1.4.0",
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "org.reactivemongo" %% "reactivemongo-play-json" % "0.13.0-play26",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "com.lightbend.akka" %% "akka-stream-alpakka-elasticsearch" % "0.19",
  "com.typesafe.play" %% "play-json" % "2.6.9",
  "io.spray" %% "spray-json" % "1.3.4"

)
