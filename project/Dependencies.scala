import sbt._

object Versions {
  val Juju = "0.1.0-SNAPSHOT"
  val Scala = "2.11.7"
  val Akka = "2.4.1"
  val Slf4jSimple = "1.7.13"
  val ScalaLogging = "3.1.0"
  val ScalaTest = "2.2.4"
  val ReactiveX = "0.25.1"
  val Spray   = "1.3.3"
  val SprayJson   = "1.3.2"
}

object Dependencies {
  import Versions._
  object Compile {
    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % ScalaLogging intransitive()
    val reactivex = "io.reactivex" %% "rxscala" % ReactiveX
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % Akka
    val slf4jSimple = "org.slf4j" % "slf4j-simple" % Slf4jSimple
    val akkaPersistence = "com.typesafe.akka" %% "akka-persistence" % Akka
    val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % Akka
    val akkaRemote = "com.typesafe.akka" %% "akka-remote" % Akka
    val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % Akka
    val akkaClusteTools = "com.typesafe.akka" %% "akka-cluster-tools" % Akka
    val akkaContrib = "com.typesafe.akka" %% "akka-contrib" % Akka

    val akkaStream = "com.typesafe.akka" %% "akka-stream-experimental" % "2.0.2"
    val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core-experimental" % "2.0.2"
    val akkaHttp = "com.typesafe.akka" %% "akka-http-experimental" % "2.0.2"
    val akkaHttpJson = "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "2.0.2"

    val scalaReflect = "org.scala-lang" % "scala-reflect" % Scala intransitive()
    val sprayCan = "io.spray" %% "spray-can" % Spray intransitive()
    val sprayRouting = "io.spray" %% "spray-routing" % Spray
    val sprayJson = "io.spray" %%  "spray-json" % SprayJson  intransitive()
    val sprayClient = "io.spray" %% "spray-client" % Spray % "test" intransitive()
    val sprayTest = "io.spray" %% "spray-testkit" % Spray % "test" intransitive()
    val scalaTest = "org.scalatest" %% "scalatest" % ScalaTest % "test"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Akka % "test"
  }
  import Compile._
  val core = Seq(scalaReflect, akkaActor, slf4jSimple, akkaPersistence, scalaLogging, reactivex, akkaTestkit, scalaTest)
  val jujuCluster = Seq(akkaRemote, akkaCluster, akkaClusterSharding, akkaClusteTools, akkaContrib)
  val jujuHttp = Seq(/*akkaStream, akkaHttp, akkaHttpCore, akkaHttpJson,*/ sprayCan, sprayRouting, sprayClient, sprayJson, reactivex, sprayTest)
}
