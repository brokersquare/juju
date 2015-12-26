package juju.infrastructure.cluster

import akka.actor.ActorRef
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{UnsubscribeAck, Publish, SubscribeAck}
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.{Saga, SagaFactory}
import juju.infrastructure.{EventBus, SagaRouter}
import juju.infrastructure.cluster.ClusterSagaRouter.SagaRouterStopped
import juju.messages.DomainEvent
import juju.testkit.ClusterDomainSpec
import juju.testkit.infrastructure.SagaRouterSpec

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Success, Try}

class ClusterSagaRouterSpec extends ClusterDomainSpec("ClusterSagaRouter") with SagaRouterSpec {
  import SagaRouter._
  import EventBus._

  val mediator = DistributedPubSub(system).mediator

  //var routers : Set[ActorRef] = Set.empty

  override protected def createSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String): ActorRef = {
    /*routers = servers.map { s =>
      ClusterDomainSpec.createRouter[S](tenant)(s)
    }*/

    val sagaName = implicitly[ClassTag[S]].runtimeClass.getSimpleName
    val name = s"${sagaName}Router"

    Try(ClusterSharding(system).shardRegion(name)) match {
      case Success(ref) => {
        system.log.warning(s"detected existing router region $name. waiting for shutdown. Retry in 3 sec")
        Thread.sleep(3000)
        createSagaRouter(tenant)
      }
      case scala.util.Failure(ex) if ex.isInstanceOf[IllegalArgumentException] => {
        mediator ! akka.cluster.pubsub.DistributedPubSubMediator.Subscribe(nameWithTenant(tenant, classOf[SagaRouterStopped]), this.testActor)
        expectMsgPF(timeout.duration){case SubscribeAck(_) => }

        mediator ! akka.cluster.pubsub.DistributedPubSubMediator.Subscribe(nameWithTenant(tenant, classOf[SagaIsUp]), this.testActor)
        expectMsgPF(timeout.duration){case SubscribeAck(_) => }

        ClusterDomainSpec.createRouter[S](tenant)(system)
      }
    }
  }

  override protected def publish(tenant: String, sagaRouterRef: ActorRef, event: DomainEvent) = {
    system.log.info(s"[$tenant]publishing $event")
    mediator ! Publish(topic = nameWithTenant(tenant, event.getClass), event, sendOneMessageToEachGroup = true)
  }

  override protected def shutdownRouter[S <: Saga : ClassTag](tenant: String, sagaRouterRef: ActorRef): Unit = {
    val sagaName = implicitly[ClassTag[S]].runtimeClass.getSimpleName

    sagaRouterRef ! ClusterSagaRouter.ShutdownSagaRouter
    expectMsgPF(timeout.duration) {
      case ClusterSagaRouter.SagaRouterStopped(_) =>
    }

    mediator ! akka.cluster.pubsub.DistributedPubSubMediator.Unsubscribe(nameWithTenant(tenant, classOf[SagaRouterStopped]), this.testActor)
    expectMsgPF(timeout.duration) {
      case UnsubscribeAck(_) =>
    }

    mediator ! akka.cluster.pubsub.DistributedPubSubMediator.Unsubscribe(nameWithTenant(tenant, classOf[SagaIsUp]), this.testActor)
    expectMsgPF(timeout.duration){
      case UnsubscribeAck(_) =>
    }

    akka.pattern.gracefulStop(sagaRouterRef, 100 seconds, stopMessage = ShardRegion.GracefulShutdown)
    logger.debug(s"sagaRouter stopped")
    /*
    routers foreach { r =>
      akka.pattern.gracefulStop(r, 100 seconds, stopMessage = ShardRegion.GracefulShutdown)
    }*/
  }
}
