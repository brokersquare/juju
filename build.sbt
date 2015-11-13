organization  := "com.brokersquare"

name := "juju"

version := "0.1.0-SNAPSHOT"

scalaVersion  := "2.11.7"

scalacOptions := Seq("-language:postfixOps", "-feature", "-deprecation")

resolvers ++= Seq(
  "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven",
  "jdgoldie at bintray" at "http://dl.bintray.com/jdgoldie/maven"
  //"buildo mvn" at "https://raw.github.com/buildo/mvn/master/releases"
)

libraryDependencies ++= {
  val Akka           = "2.3.9"
  val ScalaLogging =  "3.1.0"
  val ScalaTest      = "2.2.4"
  val kafka          = "0.8.2.1"
  val ReactiveX = "0.25.0"
  val KafkaPlugin = "0.4"
  val AkkaKafkaPersistence = "2.3.9"
  val AkkaPersistenceInMemory = "1.0.16"
  val LogBack = "1.1.3"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % Akka,
    "com.typesafe.akka" %% "akka-slf4j" % Akka,
    "com.typesafe.akka" %% "akka-persistence-experimental" % AkkaKafkaPersistence,
    "com.github.jdgoldie" %% "akka-persistence-shared-inmemory" % AkkaPersistenceInMemory intransitive(),
    "com.typesafe.scala-logging" %% "scala-logging" % ScalaLogging,
    "com.typesafe.akka" %% "akka-testkit" % Akka % "test",
    "org.scalatest" %% "scalatest" % ScalaTest % "test"
  )
}

//publishTo := Some(Resolver.file("file", new File("releases")))
