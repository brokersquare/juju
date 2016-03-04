package juju.infrastructure.kafka

import akka.actor.ActorRef
import juju.domain.{SagaFactory, Saga}
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.infrastructure.SagaRouter.SagaIsUp
import juju.infrastructure.local.LocalSagaRouter
import juju.messages.DomainEvent
import juju.testkit.LocalDomainSpec
import juju.testkit.infrastructure.SagaRouterSpec
import akka.pattern.gracefulStop
import scala.concurrent.duration._

import scala.reflect.ClassTag

class KafkaSagaRouterSpec extends LocalDomainSpec("KafkaSagaRouter") with SagaRouterSpec {
  override protected def createSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String): ActorRef = {
    system.eventStream.subscribe(self, classOf[SagaIsUp])
    LocalSagaRouter.localSagaRouterFactory(tenant).getOrCreate
  }

  override protected def publish(tenant: String, sagaRouterRef : ActorRef, event: DomainEvent) = {
    sagaRouterRef ! event
  }

  override protected def shutdownRouter[S <: Saga : ClassTag](tenant: String, sagaRouterRef: ActorRef): Unit = gracefulStop(sagaRouterRef, 10 seconds)
}
