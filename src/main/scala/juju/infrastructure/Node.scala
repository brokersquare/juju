package juju.infrastructure

import akka.actor.{Actor, ActorSystem}
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain._

import scala.reflect.ClassTag

trait Node {
  actor: Actor =>

  implicit val system: ActorSystem = actor.context.system
  protected implicit def officeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](implicit system : ActorSystem): OfficeFactory[A]
  protected implicit def sagaRouterFactory[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](implicit system : ActorSystem) : SagaRouterFactory[S]
}
