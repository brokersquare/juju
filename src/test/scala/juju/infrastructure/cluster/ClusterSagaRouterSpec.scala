package juju.infrastructure.cluster

import akka.actor.ActorRef
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, SubscribeAck}
import akka.cluster.sharding.{ShardRegion, ClusterSharding}
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.{Saga, SagaFactory}
import juju.messages.DomainEvent
import juju.testkit.ClusterDomainSpec
import juju.testkit.infrastructure.SagaRouterSpec

import scala.reflect.ClassTag
import scala.util.{Success, Try}
import scala.concurrent.duration._

class ClusterSagaRouterSpec extends ClusterDomainSpec("ClusterSagaRouter") with SagaRouterSpec {
  val mediator = DistributedPubSub(system).mediator

  //val state = Cluster(system).state
  //system.log.debug("check state...")
  /*
  val offices = servers.map {
    ClusterDomainSpec.createOffice[PriorityAggregate]
  }

  override protected def subscribeDomainEvents(): Unit = {
    val events = Seq(classOf[PriorityCreated].getSimpleName,classOf[PriorityIncreased].getSimpleName)

    val mediator = DistributedPubSub(system).mediator
    events.foreach { e =>
      mediator ! Subscribe(e, None, this.testActor)
      expectMsg(SubscribeAck(Subscribe(e, None, this.testActor)))
    }
  }
  */

  override protected def createSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String): ActorRef = {
    val sagaName = implicitly[ClassTag[S]].runtimeClass.getSimpleName
    val name = s"${sagaName}Router"
    Try(ClusterSharding(system).shardRegion(name)) match {
      case Success(ref) => {
        system.log.warning(s"detected existing router region $name. waiting for shutdown. Retry in 3 sec")
        Thread.sleep(3000)
        createSagaRouter(tenant)
      }
      case scala.util.Failure(ex) if ex.isInstanceOf[IllegalArgumentException] => {
        mediator ! akka.cluster.pubsub.DistributedPubSubMediator.Subscribe(sagaName, this.testActor)
        expectMsgPF(timeout.duration){case SubscribeAck(_) => }
        ClusterSagaRouter.clusterSagaRouterFactory(tenant).getOrCreate
      }
    }
  }

  override protected def publish(tenant: String, sagaRouterRef: ActorRef, event: DomainEvent) = {
    mediator ! Publish(topic = tenant+"_"+event.getClass.getSimpleName, event, sendOneMessageToEachGroup = true)
  }

  override protected def shutdownRouter(sagaRouterRef: ActorRef): Unit = {
    sagaRouterRef ! ClusterSagaRouter.ShutdownSagaRouter
    /*expectMsgPF(timeout.duration) {
      case ClusterSagaRouter.SagaRouterStopped(_) =>
    }*/
    sagaRouterRef ! ShardRegion.GracefulShutdown
    akka.pattern.gracefulStop(sagaRouterRef, 100 seconds, stopMessage = ShardRegion.GracefulShutdown)
    logger.debug(s"sagaRouter stopped")
  }
}
