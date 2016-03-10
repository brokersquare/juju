package juju.infrastructure.local

import akka.actor.ActorRef
import akka.testkit.TestProbe
import juju.domain.{SagaFactory, Saga}
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.infrastructure.SagaRouter.SagaIsUp
import juju.messages.DomainEvent
import juju.testkit.LocalDomainSpec
import juju.testkit.infrastructure.SagaRouterSpec
import akka.pattern.gracefulStop
import scala.concurrent.duration._

import scala.reflect.ClassTag

class LocalSagaRouterSpec extends LocalDomainSpec("LocalSagaRouter") with SagaRouterSpec {
  override protected def createSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String, probe: TestProbe): ActorRef = {
    system.eventStream.subscribe(probe.ref, classOf[SagaIsUp])
    LocalSagaRouter.localSagaRouterFactory(tenant).getOrCreate
  }

  override protected def publish(tenant: String, sagaRouterRef : ActorRef, event: DomainEvent, probe: TestProbe) = {
    probe.send(sagaRouterRef, event)
  }

  override protected def shutdownRouter[S <: Saga : ClassTag](tenant: String, sagaRouterRef: ActorRef, probe: TestProbe): Unit = {
    system.eventStream.unsubscribe(probe.ref, classOf[SagaIsUp])
    gracefulStop(sagaRouterRef, 10 seconds)
  }
}
