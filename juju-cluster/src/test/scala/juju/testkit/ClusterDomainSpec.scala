package juju.testkit

import java.net.ServerSocket

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.Saga.{SagaHandlersResolution, SagaCorrelationIdResolution}
import juju.domain.{Saga, SagaFactory, AggregateRoot, AggregateRootFactory}
import juju.infrastructure.cluster.{ClusterSagaRouter, ClusterNode, ClusterOffice}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.ClassTag

object ClusterDomainSpec {
  val clusterConfig = ConfigFactory.parseResourcesAnySyntax("domain.conf")
    .withFallback(ConfigFactory.parseResourcesAnySyntax("cluster.conf"))

  def createConfig(seedPort: String, port: String, config: Config) = {
    ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
      .withFallback(ConfigFactory.parseString("akka.cluster.seed-nodes=[\"akka.tcp://ClusterSystem" + seedPort + "@127.0.0.1:" + seedPort + "\"]"))
      .withFallback(config)
  }

  def createSystem (seed: String, port: String, config: Config): ActorSystem =
    ActorSystem(s"ClusterSystem$seed", createConfig(seed, port, config))

  def createOffice[A <: AggregateRoot[_]: AggregateIdResolution : AggregateRootFactory : ClassTag](tenant: String)(system : ActorSystem) = {
    implicit val s = system
    ClusterOffice.clusterOfficeFactory[A](tenant).getOrCreate
  }

  def createRouter[S <: Saga : SagaCorrelationIdResolution : SagaFactory: SagaHandlersResolution : ClassTag](tenant: String)(system : ActorSystem) = {
    implicit val s = system
    ClusterSagaRouter.clusterSagaRouterFactory(tenant).getOrCreate
  }


  def getAvailablePort: Int = {
    val socket = new ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port
  }
}

abstract class ClusterDomainSpec (test: String, _config: Config = ClusterDomainSpec.clusterConfig, ports:Seq[String] = Seq("0"))
  extends {
    val seedPort: Int = ClusterDomainSpec.getAvailablePort
    val servers = (Seq(seedPort.toString) ++ ports) map { port => ClusterDomainSpec.createSystem(seedPort.toString, port, _config) }
    override implicit val system : ActorSystem = servers.head

    override val config : Config = system.settings.config
  } with AkkaSpec with ClusterNode {
  behavior of test      //this will print the behavior of the test

  override def afterAll() = {
    servers foreach { s =>
      s.terminate()
      Await.result(s.whenTerminated, 10 seconds)
    }
  }
}