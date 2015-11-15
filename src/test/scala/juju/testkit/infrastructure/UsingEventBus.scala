package juju.testkit.infrastructure

import akka.actor.{PoisonPill, ActorRef, ActorSystem}
import akka.testkit.TestKitBase
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.{SagaFactory, Saga, AggregateRootFactory, AggregateRoot}
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.infrastructure.{EventBus, SagaRouterFactory, OfficeFactory}
import juju.testkit.DeadLetterRouter

import scala.reflect.ClassTag

trait UsingEventBus extends TestKitBase {
  protected implicit def officeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](implicit system : ActorSystem): OfficeFactory[A]
  protected implicit def sagaRouterFactory[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](implicit system : ActorSystem) : SagaRouterFactory[S]

  protected def withEventBus(action : ActorRef => Unit) = {
    system.eventStream.unsubscribe(this.testActor)
    var router : ActorRef = null
    var busRef: ActorRef = null

    try {
      router = system.actorOf(DeadLetterRouter.props(this.testActor))
      busRef = system.actorOf(EventBus.props())
      action(busRef)
    } finally {
      system.eventStream.unsubscribe(this.testActor)

      if (router != null) router ! PoisonPill
      if (busRef != null) busRef ! PoisonPill
    }
  }
}
