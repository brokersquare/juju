package juju.infrastructure.cluster
/*
import akka.actor.ActorRef
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.{Saga, SagaFactory}
import juju.testkit.ClusterDomainSpec
import juju.testkit.infrastructure.SagaRouterSpec

import scala.reflect.ClassTag

class ClusterSagaRouterSpec extends ClusterDomainSpec("ClusterSagaRouter") with SagaRouterSpec {
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

  override protected def getSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](): ActorRef = ClusterSagaRouter.clusterSagaRouterFactory.getOrCreate
}
*/