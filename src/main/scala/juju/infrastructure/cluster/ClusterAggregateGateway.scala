package juju.infrastructure.cluster

import akka.actor.{ActorRef, ActorLogging, Actor, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.serialization.Serialization
import juju.messages.{DomainEvent, Command}

class ClusterAggregateGateway(aggregateProps: Props) extends Actor with ActorLogging {
  val address = Serialization.serializedActorPath(self)
  var aggregateRef : Option[ActorRef] = None
  val mediator = DistributedPubSub(context.system).mediator

  //TODO: set supervisor strategy

  override def receive: Receive = {
    case cmd : Command =>
      val ref  = aggregateRef match {
        case Some(r) => r
        case None =>
          aggregateRef = Some(context.actorOf(aggregateProps, self.path.name))
          log.debug(s"[$address]created instance of aggregate ${aggregateRef.get}")
          aggregateRef.get
      }
      ref ! cmd //TODO: do retry in case of timeout???
      log.debug(s"[$address]route $cmd to aggregate ${aggregateRef.get}")
    case event : DomainEvent =>
      mediator ! Publish(event.getClass.getSimpleName, event)
      log.debug(s"[$address]received $event and published to ${event.getClass.getSimpleName}")
    case e => log.debug(s"[$address]detected message $e")
  }
}