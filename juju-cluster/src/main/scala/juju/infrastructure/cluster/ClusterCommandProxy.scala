package juju.infrastructure.cluster

import akka.actor._
import akka.cluster.routing.{ClusterRouterGroupSettings, ClusterRouterGroup}
import akka.routing.ConsistentHashingGroup
import akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope
import juju.infrastructure.cluster.ClusterCommandProxy.NotACommandException
import juju.infrastructure.{EventBus, CommandProxyFactory}
import juju.messages.{Activate, Command}

class ClusterCommandProxyFactory(tenant : String = "", useRole : Option[String] = None, allowLocalBus: Boolean = false)(implicit aSystem: ActorSystem) extends CommandProxyFactory {
  override def actor: ActorRef = {
    aSystem.actorOf(Props(new ClusterCommandProxy(tenant, useRole, allowLocalBus)), "client")
  }
}

object ClusterCommandProxy {
  @SerialVersionUID(1L) final case class NotACommandException (message: String) extends Exception(message)
}

class ClusterCommandProxy(tenant : String, useRole : Option[String], allowLocalBus: Boolean) extends Actor with ActorLogging {
  val routeesPaths = s"/user/${EventBus.actorName(tenant)}*" :: Nil
  val routerConfiguration = ClusterRouterGroup(
    ConsistentHashingGroup(routeesPaths),
    ClusterRouterGroupSettings(
      totalInstances = 10000,
      routeesPaths = routeesPaths,
      allowLocalRoutees = allowLocalBus,
      useRole = useRole
    )
  )

  val router = context.actorOf(routerConfiguration.props(), EventBus.proxyActorName(tenant))

  override def receive: Receive = {
    case m : Command =>
      router.tell(ConsistentHashableEnvelope(m, m.hashCode()), sender())
    case m : Activate =>
      router.tell(ConsistentHashableEnvelope(m, m.hashCode()), sender())
    case m =>
      sender() ! akka.actor.Status.Failure(NotACommandException(s"Cannot route message $m. It's different than either Command or Activate"))
  }
}