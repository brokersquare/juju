package juju.infrastructure.local

import akka.actor.ActorSystem
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain._
import juju.infrastructure.{Node, OfficeFactory, SagaRouterFactory}

import scala.reflect.ClassTag

trait LocalNode extends Node {
  override protected implicit def officeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](implicit system : ActorSystem): OfficeFactory[A] = LocalOffice.localOfficeFactory(tenant)
  override protected implicit def sagaRouterFactory[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](implicit system : ActorSystem): SagaRouterFactory[S] = LocalSagaRouter.localSagaRouterFactory(tenant)
}
