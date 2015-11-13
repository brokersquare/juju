package juju.infrastructure.local

import akka.actor.ActorSystem
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.{AggregateRoot, AggregateRootFactory, Saga, SagaFactory}

import scala.reflect.ClassTag

object LocalEventBus {
  implicit def officeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](implicit system : ActorSystem) = LocalOffice.localOfficeFactory
  implicit def sagaRouterFactory[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](implicit system : ActorSystem) = LocalSagaRouter.localSagaRouterFactory
}
