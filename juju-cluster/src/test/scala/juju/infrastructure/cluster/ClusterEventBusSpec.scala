package juju.infrastructure.cluster

import akka.actor.{ActorRef, PoisonPill}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, Unsubscribe}
import juju.infrastructure.EventBus
import juju.testkit.ClusterDomainSpec
import juju.testkit.infrastructure.EventBusSpec

class ClusterEventBusSpec extends ClusterDomainSpec("ClusterEventBus") with EventBusSpec {
  override def withEventBus(subscriber: ActorRef, subscribedEvents : Seq[Class[_]])(action : ActorRef => Unit) = {
    val events = subscribedEvents map(_.getSimpleName)
    var busRef: ActorRef = null
    val mediator = DistributedPubSub(system).mediator

    try {
      busRef = system.actorOf(EventBus.props(tenant))
      val subscriptionGroup = "testGroup"
      events.foreach { e =>
        mediator ! Subscribe(EventBus.nameWithTenant(tenant, e), Some(EventBus.nameWithTenant(tenant, subscriptionGroup)), subscriber)
      }

      action(busRef)
    } finally {
      events.foreach { e =>
        mediator ! Unsubscribe(EventBus.nameWithTenant(tenant, e), None, subscriber)
      }

      if (busRef != null) busRef ! PoisonPill
    }
  }
}

