import sbt._

object Versions {
  val Juju = "0.1.0-SNAPSHOT"
  val KafkaPersistence  = "7b35f752a9"
  val Scala             = "2.11.7"
  val Akka              = "2.4.1"
  val AkkaHttp          = "2.0.3"
  val Slf4jSimple       = "1.7.13"
  val ScalaLogging      = "3.1.0"
  val ReactiveX         = "0.25.1"
  val SprayJson         = "1.3.2"

  val Kamon             = "0.5.2"

  val CommonIO          = "2.4"
  val Curator           = "2.7.1"
  val ScalaTest         = "2.2.4"
}

object Dependencies {
  import Versions._
  object Compile {
    val scalaLogging        = "com.typesafe.scala-logging" %% "scala-logging" % ScalaLogging intransitive()
    val reactivex           = "io.reactivex" %% "rxscala" % ReactiveX
    val akkaActor           = "com.typesafe.akka" %% "akka-actor" % Akka
    val slf4jSimple         = "org.slf4j" % "slf4j-simple" % Slf4jSimple
    val akkaPersistence     = "com.typesafe.akka" %% "akka-persistence" % Akka
    val akkaCluster         = "com.typesafe.akka" %% "akka-cluster" % Akka
    val akkaRemote          = "com.typesafe.akka" %% "akka-remote" % Akka
    val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % Akka
    val akkaClusteTools     = "com.typesafe.akka" %% "akka-cluster-tools" % Akka
    val akkaDistData        = "com.typesafe.akka" %% "akka-distributed-data-experimental" % Akka
    val akkaContrib         = "com.typesafe.akka" %% "akka-contrib" % Akka

    val kafkaPersistence    = "com.github.brokersquare" %% "akka-persistence-kafka" % KafkaPersistence excludeAll(ExclusionRule("org.slf4j", "slf4j-simple"),ExclusionRule("org.slf4j", "slf4j-log4j12"))

    val akkaHttpCore        = "com.typesafe.akka" %% "akka-http-core-experimental" % AkkaHttp
    val akkaHttp            = "com.typesafe.akka" %% "akka-http-experimental" % AkkaHttp
    val akkaHttpJson        = "com.typesafe.akka" %% "akka-http-spray-json-experimental" % AkkaHttp
    val akkaHttpTestKit     = "com.typesafe.akka" %% "akka-http-testkit-experimental" % AkkaHttp

    val kamonCore           = "io.kamon" %% "kamon-core" % Kamon
    val kamonScala          = "io.kamon" %% "kamon-scala" % Kamon
    val kamonAkka           = "io.kamon" %% "kamon-akka" % Kamon
    val kamonSystemMetrics  = "io.kamon" %% "kamon-system-metrics" % Kamon
    val kamonStatsd         = "io.kamon" %% "kamon-statsd" % Kamon
    val kamonLogReporter    = "io.kamon" %% "kamon-log-reporter" % Kamon

    val scalaReflect        = "org.scala-lang" % "scala-reflect" % Scala intransitive()
    val scalaTest           = "org.scalatest" %% "scalatest" % ScalaTest % "test"
    val akkaTestkit         = "com.typesafe.akka" %% "akka-testkit" % Akka % "test"

    val commonIO            = "commons-io" % "commons-io"  % CommonIO % "test"
    val curator             = "org.apache.curator" % "curator-test" % Curator % "test"
  }

  import Compile._
  val core = Seq(scalaReflect, akkaActor, slf4jSimple, akkaPersistence, scalaLogging, reactivex, akkaTestkit, scalaTest)
  val jujuCluster = Seq(akkaRemote, akkaCluster, akkaClusterSharding, akkaClusteTools, akkaContrib, akkaDistData)
  val jujuHttp = Seq(akkaHttp, akkaHttpCore, akkaHttpJson, akkaHttpTestKit, reactivex)
  val jujuKafka = Seq(kafkaPersistence, commonIO, curator)
  val jujuMetrics = Seq(kamonCore, kamonAkka, kamonScala, kamonSystemMetrics, kamonLogReporter, kamonStatsd)
}
