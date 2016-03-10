package juju.messages

import akka.actor.ActorRef

case object ShutdownActor extends InfrastructureMessage
case class ShutdownActorCompleted(actor: ActorRef) extends InfrastructureMessage
