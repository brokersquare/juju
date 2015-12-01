package juju.infrastructure.local

import akka.actor.ActorRef
import juju.domain.{SagaFactory, Saga}
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.messages.DomainEvent
import juju.testkit.LocalDomainSpec
import juju.testkit.infrastructure.SagaRouterSpec
import akka.pattern.gracefulStop
import scala.concurrent.duration._

import scala.reflect.ClassTag

class LocalSagaRouterSpec extends LocalDomainSpec("LocalSagaRouter") with SagaRouterSpec {
  override protected def createSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String): ActorRef = LocalSagaRouter.localSagaRouterFactory(tenant).getOrCreate

  override protected def publish(tenant: String, sagaRouterRef : ActorRef, event: DomainEvent) = {
    sagaRouterRef ! event
  }

  override protected def shutdownRouter(sagaRouterRef: ActorRef): Unit = gracefulStop(sagaRouterRef, 10 seconds)
}
