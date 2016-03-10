package juju.testkit

import java.io.File

import akka.actor.ActorSystem
import akka.persistence.kafka.server.{TestServer, TestServerConfig}
import com.typesafe.config.{Config, ConfigFactory}
import juju.infrastructure.local.LocalNode
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll

import scala.util.Try

object KafkaDomainSpec {
  def getLocalConnectString(port: Int) = "\"localhost:\"" + port

}

abstract class KafkaDomainSpec (val test: String, zookeeperPort: Int, _config: Config = ConfigFactory.load("kafka.conf"))
  extends {
  override val config : Config = ConfigFactory
    .parseString(s"test-server.zookeeper.port=$zookeeperPort")
    .withFallback(ConfigFactory.parseString(s"kafka-journal.zookeeper.connect=${KafkaDomainSpec.getLocalConnectString(zookeeperPort)}"))
    .withFallback(ConfigFactory.parseString(s"kafka-snapshot-store.zookeeper.connect=${KafkaDomainSpec.getLocalConnectString(zookeeperPort)}"))
    .withFallback(ConfigFactory.parseString(s"test-server.kafka.port=${scala.math.abs(6667 + zookeeperPort - 2181)}"))
    .withFallback(ConfigFactory.parseString(s"test-server.kafka.broker.id=${scala.math.abs(zookeeperPort - 2181)}"))
    .withFallback(ConfigFactory.parseString(s"test-server.zookeeper.dir=target/test/$test/zookeeper"))
    .withFallback(ConfigFactory.parseString(s"test-server.kafka.log.dirs=target/test/$test/kafka"))
    .withFallback(_config)
  override implicit val system = ActorSystem(test, config)


  val systemConfig = system.settings.config
  val serverConfig = new TestServerConfig(systemConfig.getConfig("test-server"))
  val server = new TestServer(serverConfig)

} with AkkaSpec with LocalNode with BeforeAndAfterAll {
  behavior of test      //this will print the behavior of the test

  override def afterAll(): Unit = {
      val folders = Seq(s"target/test/$test/zookeeper", s"target/test/$test/kafka", "target/test")

      folders map (new File(_)) foreach { f =>
        Try(FileUtils.deleteDirectory(f)) match {
          case scala.util.Success(_) =>
            logger.debug(s"deleted file ${f.getName}")
          case scala.util.Failure(cause) =>
            logger.warn(s"cannot delete ${f.getName} due to $cause")
        }
      }
  }
}
