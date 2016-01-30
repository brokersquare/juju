package juju.infrastructure.local

import akka.actor.{Props, ActorRef, ActorContext}
import juju.infrastructure.{AppSchedulerFactory, AppScheduler, EventBus}

class LocalAppSchedulerFactory extends AppSchedulerFactory {
  override def create(tenant: String, role: String, context: ActorContext): ActorRef = {
    context.actorOf(Props(classOf[AppScheduler], tenant), EventBus.nameWithTenant(tenant, s"${role}_scheduler")))
  }
}
