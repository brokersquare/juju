package juju.testkit

import akka.actor.ActorSystem
import com.typesafe.config.{ConfigFactory, Config}
import juju.infrastructure.cluster.ClusterNode

abstract class ClusterDomainSpec (test: String, _config: Config = ConfigFactory.load("cluster.conf").withFallback(ConfigFactory.load("domain.conf")) ) extends {
  override val config : Config = _config
  override implicit val system = ActorSystem(test, config)
} with AkkaSpec with ClusterNode {
  behavior of test      //this will print the behavior of the test
}