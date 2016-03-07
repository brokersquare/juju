package juju.infrastructure.cluster

import akka.actor.ActorRef
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, SubscribeAck, UnsubscribeAck}
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import akka.testkit.TestProbe
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.{Saga, SagaFactory}
import juju.infrastructure.cluster.ClusterSagaRouter.SagaRouterStopped
import juju.infrastructure.{EventBus, SagaRouter}
import juju.messages.DomainEvent
import juju.testkit.ClusterDomainSpec
import juju.testkit.infrastructure.SagaRouterSpec

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Success, Try}

class ClusterSagaRouterSpec extends ClusterDomainSpec("ClusterSagaRouter") with SagaRouterSpec {
  import EventBus._
  import SagaRouter._

  val mediator = DistributedPubSub(system).mediator

  override protected def createSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String, probe: TestProbe): ActorRef = {

    val sagaName = implicitly[ClassTag[S]].runtimeClass.getSimpleName
    val name = s"${sagaName}Router"

    Try(ClusterSharding(system).shardRegion(name)) match {
      case Success(ref) => {
        system.log.warning(s"detected existing router region $name. waiting for shutdown. Retry in 3 sec")
        Thread.sleep(3000)
        createSagaRouter(tenant, probe)
      }
      case scala.util.Failure(ex) if ex.isInstanceOf[IllegalArgumentException] => {
        probe.send(mediator, akka.cluster.pubsub.DistributedPubSubMediator.Subscribe(nameWithTenant(tenant, classOf[SagaRouterStopped]), probe.ref))
        probe.expectMsgPF(timeout.duration){case SubscribeAck(_) => }

        probe.send(mediator, akka.cluster.pubsub.DistributedPubSubMediator.Subscribe(nameWithTenant(tenant, classOf[SagaIsUp]), probe.ref))
        probe.expectMsgPF(timeout.duration){case SubscribeAck(_) => }

        ClusterDomainSpec.createRouter[S](tenant)(system)
      }
    }
  }

  override protected def publish(tenant: String, sagaRouterRef: ActorRef, event: DomainEvent, probe: TestProbe) = {
    system.log.info(s"[$tenant]publishing $event")
    val topic = nameWithTenant(tenant, event.getClass)
    probe.send(mediator, Publish(topic, event, sendOneMessageToEachGroup = true))
  }

  override protected def shutdownRouter[S <: Saga : ClassTag](tenant: String, sagaRouterRef: ActorRef, probe: TestProbe): Unit = {
    val sagaName = implicitly[ClassTag[S]].runtimeClass.getSimpleName
    val subscriber = probe.ref
    probe.ignoreNoMsg()
    probe.ignoreMsg {
      case _ : SagaRouterStopped => false
      case _ : UnsubscribeAck => false
      case _  => true
    }

    probe.send(sagaRouterRef, ClusterSagaRouter.ShutdownSagaRouter)
    probe.expectMsgPF(timeout.duration) {
      case ClusterSagaRouter.SagaRouterStopped(_) =>
    }

    probe.send(mediator, akka.cluster.pubsub.DistributedPubSubMediator.Unsubscribe(nameWithTenant(tenant, classOf[SagaRouterStopped]), subscriber))
    probe.expectMsgPF(timeout.duration) {
      case UnsubscribeAck(_) =>
    }

    probe.send(mediator, akka.cluster.pubsub.DistributedPubSubMediator.Unsubscribe(nameWithTenant(tenant, classOf[SagaIsUp]), subscriber))
    probe.expectMsgPF(timeout.duration){
      case UnsubscribeAck(_) =>
    }

    akka.pattern.gracefulStop(sagaRouterRef, 100 seconds, stopMessage = ShardRegion.GracefulShutdown)
    logger.debug(s"sagaRouter stopped")
  }
}
