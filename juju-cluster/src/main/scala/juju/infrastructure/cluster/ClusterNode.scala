package juju.infrastructure.cluster

import akka.actor.ActorSystem
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain._
import juju.infrastructure.{AppSchedulerFactory, Node, OfficeFactory, SagaRouterFactory}

import scala.reflect.ClassTag

trait ClusterNode extends Node {
  override protected implicit def officeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](implicit system : ActorSystem): OfficeFactory[A] = ClusterOffice.clusterOfficeFactory(tenant)
  override protected implicit def sagaRouterFactory[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](implicit system : ActorSystem): SagaRouterFactory[S] = ClusterSagaRouter.clusterSagaRouterFactory(tenant)
  override protected def schedulerFactory: AppSchedulerFactory = new ClusterAppSchedulerFactory()
}
