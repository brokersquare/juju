import sbt._

object Dependencies {
  import Versions._
  object Compile {
    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % ScalaLogging
    val reactivex = "io.reactivex" %% "rxscala" % ReactiveX
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % Akka
    val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % Akka
    val akkaPersistence = "com.typesafe.akka" %% "akka-persistence" % Akka
    val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % Akka
    val akkaRemote = "com.typesafe.akka" %% "akka-remote" % Akka
    val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % Akka
    val akkaClusteTools = "com.typesafe.akka" %% "akka-cluster-tools" % Akka
    val akkaContrib = "com.typesafe.akka" %% "akka-contrib" % Akka
    val scalaTest = "org.scalatest" %% "scalatest" % ScalaTest % "test"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Akka % "test"
  }
  import Compile._
  val juju = Seq(akkaActor, akkaSlf4j, akkaPersistence, scalaLogging, reactivex, akkaTestkit, scalaTest)
  val jujuCluster = Seq(akkaRemote, akkaCluster, akkaClusterSharding, akkaClusteTools, akkaContrib)
}
