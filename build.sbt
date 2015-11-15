organization  := "com.brokersquare"

name := "juju"

version := Versions.Juju

scalaVersion  := Versions.Scala

scalacOptions := Seq("-language:postfixOps", "-feature", "-deprecation")

resolvers ++= Seq(
  "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven",
  "jdgoldie at bintray" at "http://dl.bintray.com/jdgoldie/maven"
)

libraryDependencies ++= {
  import Versions._
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
