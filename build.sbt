organization  := "com.brokersquare"

name := "juju"

version := Versions.Juju

scalaVersion  := Versions.Scala

scalacOptions := Seq("-language:postfixOps", "-feature", "-deprecation", "-language:implicitConversions")

resolvers ++= Seq(
  "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven",
  "jdgoldie at bintray" at "http://dl.bintray.com/jdgoldie/maven"
)

libraryDependencies ++= {
  import Versions._
  Seq(
    "com.typesafe.akka" %% "akka-actor" % Akka,
    "com.typesafe.akka" %% "akka-slf4j" % Akka,
    "com.typesafe.akka" %% "akka-persistence" % Akka,
    "com.typesafe.scala-logging" %% "scala-logging" % ScalaLogging,
    "com.typesafe.akka" %% "akka-testkit" % Akka % "test",
    "org.scalatest" %% "scalatest" % ScalaTest % "test",
    "com.typesafe.akka" %% "akka-cluster" % Akka,
    "com.typesafe.akka" %% "akka-remote" % Akka,
    "com.typesafe.akka" %% "akka-cluster-sharding" % Akka,
    "com.typesafe.akka" %% "akka-cluster-tools" % Akka,
    "com.typesafe.akka" %% "akka-contrib" % Akka,
    "io.reactivex" %% "rxscala" % ReactiveX
  )
}