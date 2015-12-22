package juju.infrastructure.cluster

import akka.actor.{ActorRef, PoisonPill}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck, Unsubscribe, UnsubscribeAck}
import juju.infrastructure.EventBus
import juju.testkit.ClusterDomainSpec
import juju.testkit.infrastructure.EventBusSpec

class ClusterEventBusSpec extends ClusterDomainSpec("ClusterEventBus") with EventBusSpec {
  override def withEventBus(subscribedEvents : Seq[Class[_]])(action : ActorRef => Unit) = {
    val events = subscribedEvents map(_.getSimpleName)
    var busRef: ActorRef = null
    val mediator = DistributedPubSub(system).mediator

    try {
      busRef = system.actorOf(EventBus.props(tenant))

      events.foreach { e =>
        mediator ! Subscribe(e, None, this.testActor)
        expectMsg(SubscribeAck(Subscribe(e, None, this.testActor)))
      }

      action(busRef)
    } finally {
      events.foreach { e =>

        mediator ! Unsubscribe(e, None, this.testActor)
        expectMsg(UnsubscribeAck(Unsubscribe(e, None, this.testActor)))
      }

      if (busRef != null) busRef ! PoisonPill
    }
  }
}