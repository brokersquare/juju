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

lazy val core = Project(id="juju-core", base=file("core"))
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++= Dependencies.core)

lazy val jujuCluster = Project(id="juju-cluster",base=file("juju-cluster"))
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++= Dependencies.jujuCluster)
  .dependsOn(core % "compile->compile;test->test")
