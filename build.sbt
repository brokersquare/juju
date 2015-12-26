lazy val defaultSettings =
    Defaults.coreDefaultSettings ++
      Seq(
        organization := "com.brokersquare",
        version := Versions.Juju,
        scalaVersion := Versions.Scala,
        scalacOptions:= Seq("-language:postfixOps", "-feature", "-deprecation", "-language:implicitConversions"),
        resolvers ++= Seq(
          "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven",
          "jdgoldie at bintray" at "http://dl.bintray.com/jdgoldie/maven"
        )
      )

lazy val juju = (project in file("."))
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++= Dependencies.juju)

lazy val jujuCluster = (project in file("juju-cluster"))
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++= Dependencies.jujuCluster)
  .dependsOn(juju % "compile->compile;test->test")
