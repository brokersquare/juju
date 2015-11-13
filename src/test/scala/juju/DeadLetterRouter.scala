package juju

import akka.actor.{Actor, ActorRef, DeadLetter, Props}


object DeadLetterRouter {
  def props(receiver: ActorRef) = Props(classOf[DeadLetterRouter], receiver)
}

class DeadLetterRouter(receiver: ActorRef) extends Actor {
  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[DeadLetter])
  }

  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(self)
  }

  override def receive: Receive = {
    case DeadLetter(m, _, _) => receiver.tell(m, sender)
    case _ => throw new Exception("Received unexpected message")
  }
}