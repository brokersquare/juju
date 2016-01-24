import sbt._

object Versions {
  val Juju = "0.1.0-SNAPSHOT"
  val Scala = "2.11.7"
  val Akka = "2.4.1"
  val ScalaLogging = "3.1.0"
  val ScalaTest = "2.2.4"
  val kafka = "0.8.2.1"
  val ReactiveX = "0.25.0"
  val KafkaPlugin = "0.4"
  val AkkaKafkaPersistence = "2.3-14"
  val LogBack = "1.1.3"
  val Spray   = "1.3.3"
  val SprayJson   = "1.3.2"
  val ScalaReflect  = "2.11.7"
}

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
    val scalaReflect = "org.scala-lang" % "scala-reflect" % ScalaReflect
    val sprayCan = "io.spray" %% "spray-can" % Spray
    val sprayRouting = "io.spray" %% "spray-routing" % Spray
    val sprayJson = "io.spray" %%  "spray-json" % SprayJson
    val sprayClient = "io.spray" %% "spray-client" % Spray % "test"
    val sprayTest = "io.spray" %% "spray-testkit" % Spray % "test"
    val scalaTest = "org.scalatest" %% "scalatest" % ScalaTest % "test"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Akka % "test"
  }
  import Compile._
  val core = Seq(akkaActor, akkaSlf4j, akkaPersistence, scalaLogging, reactivex, akkaTestkit, scalaTest)
  val jujuCluster = Seq(akkaRemote, akkaCluster, akkaClusterSharding, akkaClusteTools, akkaContrib)
  val jujuHttp = Seq(scalaReflect, sprayCan, sprayRouting, sprayClient, sprayJson, sprayTest)
}
