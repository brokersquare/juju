package juju.infrastructure.cluster

import akka.actor.{Props, ActorRef, ActorContext}
import akka.cluster.singleton.{ClusterSingletonProxySettings, ClusterSingletonProxy, ClusterSingletonManagerSettings, ClusterSingletonManager}
import juju.infrastructure.{AppScheduler, AppSchedulerFactory, EventBus}

class ClusterAppSchedulerFactory extends AppSchedulerFactory {
  case object End
  override def create(tenant: String, role: String, context: ActorContext): ActorRef = {
    val manager = context.actorOf(ClusterSingletonManager.props(
      singletonProps = Props(classOf[AppScheduler], tenant),
      terminationMessage = End,
      settings = ClusterSingletonManagerSettings(context.system).withRole(role)),
      name =  EventBus.nameWithTenant(tenant, s"${role}_scheduler"))

    val managerPath = manager.path.toString.replace(context.system.toString, "")
    val proxy = context.actorOf(ClusterSingletonProxy.props(
      singletonManagerPath = managerPath,//"/user/$a/backend_scheduler",
      settings = ClusterSingletonProxySettings(context.system).withRole(role)),
      name =  EventBus.nameWithTenant(tenant, s"${role}_schedulerProxy"))
    proxy
  }
}