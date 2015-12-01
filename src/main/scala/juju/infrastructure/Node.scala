package juju.infrastructure

import akka.actor.ActorSystem
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain._

import scala.reflect.ClassTag

trait Node {
  val tenant : String = null
  protected implicit def officeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](implicit system : ActorSystem): OfficeFactory[A]
  protected implicit def sagaRouterFactory[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](implicit system : ActorSystem) : SagaRouterFactory[S]
}
