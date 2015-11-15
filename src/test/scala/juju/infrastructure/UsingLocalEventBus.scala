package juju.infrastructure

import akka.actor.ActorSystem
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.{AggregateRoot, AggregateRootFactory, Saga, SagaFactory}
import juju.infrastructure.local.{LocalOffice, LocalSagaRouter}
import juju.testkit.infrastructure.UsingEventBus

import scala.reflect.ClassTag

trait UsingLocalEventBus extends UsingEventBus {
  override protected implicit def officeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](implicit system : ActorSystem): OfficeFactory[A] = LocalOffice.localOfficeFactory
  override protected implicit def sagaRouterFactory[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](implicit system : ActorSystem): SagaRouterFactory[S] = LocalSagaRouter.localSagaRouterFactory
}
