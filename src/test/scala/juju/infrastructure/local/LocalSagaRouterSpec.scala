package juju.infrastructure.local

import akka.actor.ActorRef
import juju.domain.{SagaFactory, Saga}
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.messages.DomainEvent
import juju.testkit.LocalDomainSpec
import juju.testkit.infrastructure.SagaRouterSpec

import scala.reflect.ClassTag

class LocalSagaRouterSpec extends LocalDomainSpec("LocalSagaRouter") with SagaRouterSpec {
  override protected def getSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](): ActorRef = LocalSagaRouter.localSagaRouterFactory.getOrCreate

  override protected def publish(sagaRouterRef : ActorRef, event: DomainEvent) = {
    sagaRouterRef ! event
  }
}
