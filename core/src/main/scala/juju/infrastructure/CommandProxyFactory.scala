package juju.infrastructure

import akka.actor.ActorRef

trait CommandProxyFactory {
  def actor : ActorRef
}
