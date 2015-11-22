package juju.testkit

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.{Config, ConfigFactory}
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.{AggregateRoot, AggregateRootFactory}
import juju.infrastructure.cluster.{ClusterNode, ClusterOffice}
import juju.sample.PriorityAggregate

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.Random

object ClusterDomainSpec {
  val clusterConfig = ConfigFactory.parseResourcesAnySyntax("domain.conf")
    .withFallback(ConfigFactory.parseResourcesAnySyntax("cluster.conf"))

  def createConfig(seedPort: String, port: String, config: Config) = {
    ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
      .withFallback(ConfigFactory.parseString("akka.cluster.seed-nodes=[\"akka.tcp://ClusterSystem@127.0.0.1:" + seedPort + "\"]"))
      .withFallback(config)
  }

  def createSystemOffice[A <: AggregateRoot[_]: AggregateIdResolution : AggregateRootFactory : ClassTag](seed: String, port: String, config: Config) = {
    val system = ActorSystem("ClusterSystem", createConfig(seed, port, config))

    implicit val s = system
    val office = ClusterOffice.clusterOfficeFactory[A].getOrCreate

    (system, office)
  }

  def startupNodes(seedPort: String, ports: Seq[String], config: Config): Map[String, (ActorSystem, ActorRef)] = {
    ((Seq(seedPort) ++ ports).toSet[String] map {port => port -> createSystemOffice[PriorityAggregate](seedPort, port, config)}).toMap
  }
}

abstract class ClusterDomainSpec (test: String, _config: Config = ClusterDomainSpec.clusterConfig)
  extends {
    val seedPort: Int = Random.shuffle(2551 to 2600).toSet.head
    val servers = ClusterDomainSpec.startupNodes(seedPort.toString, Seq("0", "0"), _config)

    override implicit val system : ActorSystem = servers.head._2._1
    override val config : Config = system.settings.config
  } with AkkaSpec with ClusterNode {
  behavior of test      //this will print the behavior of the test

  override def afterAll() = {
    servers.map(s=>s._2._1) foreach { s =>
      s.terminate()
      Await.result(s.whenTerminated, 10 seconds)
    }
  }
}