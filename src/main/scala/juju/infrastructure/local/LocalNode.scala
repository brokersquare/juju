package juju.infrastructure.local

import akka.actor.{Actor, ActorSystem}
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain._
import juju.infrastructure.{Node, OfficeFactory, SagaRouterFactory}
import juju.infrastructure.local.LocalOffice._

import scala.reflect.ClassTag

trait LocalNode extends Node {
  actor: Actor =>

  override protected implicit def officeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](implicit system : ActorSystem): OfficeFactory[A] = localOfficeFactory
  override protected implicit def sagaRouterFactory[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](implicit system : ActorSystem): SagaRouterFactory[S] = LocalSagaRouter.localSagaRouterFactory
}
