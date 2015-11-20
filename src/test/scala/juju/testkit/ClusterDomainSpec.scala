package juju.testkit

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import juju.infrastructure.cluster.ClusterNode
import juju.infrastructure.cluster.ClusterOffice.clusterOfficeFactory
import juju.sample.PriorityAggregate

import scala.reflect.ClassTag

object ClusterConfig {
  val clusterConfig = ConfigFactory.load("cluster.conf")
    .withFallback(ConfigFactory.load("domain.conf"))
  def createConfig(port: Int, config: Config) = {
    ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
      //withFallback(ConfigFactory.parseString("akka.cluster.seed-nodes=[\"akka.tcp://ClusterSystem@127.0.0.1:2551\"]")).
      withFallback(config)
  }
}

abstract class ClusterDomainSpec (test: String, _config: Config = ClusterConfig.clusterConfig)
  extends {
    val mainSeedPort = 2551
    override val config = ClusterConfig.createConfig(mainSeedPort, _config)
    override implicit val system = ActorSystem(test, config)

    val secondarySeed = ActorSystem(test, ClusterConfig.createConfig(2552, _config))
    val secondaryOffice = clusterOfficeFactory[PriorityAggregate](PriorityAggregate.idResolution, PriorityAggregate.factory,implicitly[ClassTag[PriorityAggregate]], secondarySeed)
    //val nodes = Seq(0).map(port => ActorSystem(test, ClusterConfig.createConfig(port, _config)))
   /* val offices = nodes.map(s => ClusterOffice
    .clusterOfficeFactory[PriorityAggregate](PriorityAggregate.idResolution, PriorityAggregate.factory,implicitly[ClassTag[PriorityAggregate]], s)
    )*/
  } with AkkaSpec with ClusterNode {
  behavior of test      //this will print the behavior of the test
}