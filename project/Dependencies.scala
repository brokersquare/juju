import sbt._

object Dependencies {
  import Versions._
  object Compile {
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % Akka
    val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % Akka
    val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % Akka
    val akkaRemote = "com.typesafe.akka" %% "akka-remote" % Akka
    val akkaContrib = "com.typesafe.akka" %% "akka-contrib" % Akka
    val akkaPersistence = "com.typesafe.akka" %% "akka-persistence-experimental" % AkkaKafkaPersistence
    val akkaPersistenceInMemory = "com.github.jdgoldie" %% "akka-persistence-shared-inmemory" % AkkaPersistenceInMemory
    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % ScalaLogging
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Akka % "test"
    val scalaTest = "org.scalatest" %% "scalatest" % ScalaTest % "test"
  }
  import Compile._
  val juju = Seq(akkaActor, akkaSlf4j, akkaPersistence, akkaPersistence intransitive(), scalaLogging, akkaTestkit, scalaTest)
  val jujuCluster = Seq(akkaCluster, akkaRemote, akkaContrib)
}
