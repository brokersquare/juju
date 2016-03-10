package juju.testkit

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import juju.infrastructure.local.LocalNode

abstract class LocalDomainSpec (val test: String, _config: Config = ConfigFactory.load("domain.conf")) extends {
  override val config : Config = _config
  override implicit val system = ActorSystem(test, config)
} with AkkaSpec with LocalNode {
  behavior of test      //this will print the behavior of the test
}
