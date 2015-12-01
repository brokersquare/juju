package juju.infrastructure.cluster

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}
import akka.cluster.sharding.ShardRegion.MessageExtractor
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.serialization.Serialization
import com.typesafe.config.ConfigFactory
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.{Saga, SagaFactory}
import juju.infrastructure.{SagaRouterFactory, UpdateHandlers}
import juju.messages.{Command, DomainEvent, InfrastructureMessage, Message}

import scala.reflect.ClassTag
import scala.util.Try

object ClusterSagaRouter {
  case class DispatcherMessage(index: Int, message: Message) extends InfrastructureMessage
  case object RequestUpdateHandlers extends InfrastructureMessage
  case object ShutdownSagaRouter extends InfrastructureMessage
  case class SagaRouterStopped(sagaType: String) extends InfrastructureMessage
  case class InitializeDispatcher(index: Int) extends InfrastructureMessage

  implicit def clusterSagaRouterFactory[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](_tenant: String)(implicit system: ActorSystem) = new SagaRouterFactory[S] {
    override val tenant :String = _tenant

    override def getOrCreate: ActorRef = {
    //  getOrCreateRegion(system, s"${routerName}Router", routerProps(), routerMessageExtractor[S]())
      getOrCreateRegion(system, withTenantName(tenant,s"${routerName}Router"), routerProps(tenant), routerMessageExtractor[S]())
    }
  }

  def getOrCreateRegion(system: ActorSystem, name: String, entityProps: Props, messageExtractor: MessageExtractor): ActorRef = {
    region(system, name).getOrElse {
      val settings = ClusterShardingSettings(system)
      ClusterSharding(system).start(name, entityProps, settings, messageExtractor)
      region(system, name).get
    }
  }

  def region(system: ActorSystem, name: String): Option[ActorRef] = {
    Try(ClusterSharding(system).shardRegion(name)) match {
      case scala.util.Success(ref) => Some(ref)
      case scala.util.Failure(ex) if ex.isInstanceOf[IllegalArgumentException] => None
    }
  }
  def sagaName[S <: Saga : ClassTag]() = implicitly[ClassTag[S]].runtimeClass.getSimpleName

  def routerProps[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String) = Props(new ClusterSagaRouter[S](tenant))
  def dispatcherProps[S <: Saga : ClassTag: SagaHandlersResolution](tenant: String) = Props(new ClusterSagaRouterDispatcher[S](tenant))
  def gatewayProps[S <: Saga : ClassTag: SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String, sagaRouterRef: ActorRef) = Props(new ClusterSagaRouterGateway[S](tenant, sagaRouterRef))

  def routerMessageExtractor[S <: Saga : ClassTag]() = new MessageExtractor {
    val name = s"${sagaName[S]()}Router"
    override def entityId(message: Any): String = name
    override def shardId(message: Any): String = Integer.toHexString(name.hashCode).charAt(0).toString
    override def entityMessage(message: Any): Any = message
  }

  def dispatcherMessageExtractor[S <: Saga : ClassTag]() = new MessageExtractor {
    val name = s"${sagaName[S]()}Dispatcher"
    override def entityId(message: Any): String = message match {
      case DispatcherMessage(idx, m) => idx.toString
    }
    override def shardId(message: Any): String = message match {
      case DispatcherMessage(idx, m) => Integer.toHexString(s"${name}_$idx".hashCode).charAt(0).toString
    }
    override def entityMessage(message: Any): Any =  message match {
      case DispatcherMessage(idx, m) => m
    }
  }

  def gatewayMessageExtractor[S <: Saga : ClassTag : SagaCorrelationIdResolution]() = new MessageExtractor {
    val correlationIdResolution = implicitly[SagaCorrelationIdResolution[S]]
    val name = s"${sagaName[S]()}"
    override def entityId(message: Any): String = message match {
      case event : DomainEvent if correlationIdResolution.resolve(event).isDefined => correlationIdResolution.resolve(event).get
    }
    override def shardId(message: Any): String = message match {
      case event: DomainEvent => Integer.toHexString(correlationIdResolution.resolve(event).hashCode).charAt(0).toString

    }
    override def entityMessage(message: Any): Any = message match {
      case e: DomainEvent => e
    }
  }
  
  def withTenantName(tenant: String, name: String): String = {
    if (tenant == null || tenant.trim == "") {
      name
    } else {
      s"${tenant}_$name"
    }
  }
}

class ClusterSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String) extends Actor with ActorLogging with Stash {
  import ClusterSagaRouter._
  val address = Serialization.serializedActorPath(self)
  val mediator = DistributedPubSub(context.system).mediator

  var commandHandlers : Map[Class[_ <: Command], ActorRef] = Map.empty

  val dispatcherCount = context.system.settings.config.
    withFallback(ConfigFactory.parseString("juju.cluster.dispatcher-count = 1"))
    .getInt("juju.cluster.dispatcher-count")

//  val dispatcherRegion = ClusterSagaRouter.getOrCreateRegion(context.system, s"${ClusterSagaRouter.sagaName[S]()}Dispatcher", ClusterSagaRouter.dispatcherProps(), ClusterSagaRouter.dispatcherMessageExtractor())
//  val gatewayRegion = ClusterSagaRouter.getOrCreateRegion(context.system, s"${ClusterSagaRouter.sagaName[S]()}", ClusterSagaRouter.gatewayProps(self), ClusterSagaRouter.gatewayMessageExtractor())
  val dispatcherRegion = ClusterSagaRouter.getOrCreateRegion(context.system, withTenantName(tenant, s"${ClusterSagaRouter.sagaName[S]()}Dispatcher"), ClusterSagaRouter.dispatcherProps(tenant), ClusterSagaRouter.dispatcherMessageExtractor())
  val gatewayRegion = ClusterSagaRouter.getOrCreateRegion(context.system, withTenantName(tenant, s"${ClusterSagaRouter.sagaName[S]()}"), ClusterSagaRouter.gatewayProps(tenant, self), ClusterSagaRouter.gatewayMessageExtractor())

  (0 to dispatcherCount).foreach { i =>
    dispatcherRegion ! DispatcherMessage(i, InitializeDispatcher(i))
  }

  context.become(initialize)

  var dispatcherNotInitializedCount = dispatcherCount
  def initialize: Receive = {
    case akka.actor.Status.Success(InitializeDispatcher(index)) =>
      dispatcherNotInitializedCount = dispatcherNotInitializedCount - 1
      if (dispatcherNotInitializedCount == 0) {
        log.info(s"router initialization completed. Ready to receive messages")
        context.unbecome()
        unstashAll()
      }
    case _ => stash()
  }

  override def receive: Receive = {
    case RequestUpdateHandlers => sender ! UpdateHandlers(commandHandlers)
    case uh@UpdateHandlers(h) =>
      commandHandlers = h
      mediator ! Publish(uh.getClass.getSimpleName, uh)
      sender ! akka.actor.Status.Success
    case ShutdownSagaRouter =>
      log.info(s"received shutdown. Stopping dispatcher and gateway")
      context.watch(dispatcherRegion)
      dispatcherRegion ! ShardRegion.GracefulShutdown
      context.watch(gatewayRegion)
      gatewayRegion ! ShardRegion.GracefulShutdown
      context.become(Shutdown)
    case _ =>
  }

  def Shutdown: Receive = {
    case Terminated(`dispatcherRegion`) =>
      log.info(s"dispatcher terminated")
      dispatcherTerminated = true
      shutdownIfReady()
    case Terminated(`gatewayRegion`) =>
      log.info(s"gateway terminated")
      gatewayTerminated = true
      shutdownIfReady()
    case m => {
      log.debug(s"received message $m to stash")
      stash()
    }
  }

  var dispatcherTerminated = false
  var gatewayTerminated = false
  private def shutdownIfReady() = {
    if (dispatcherTerminated && gatewayTerminated) {
      log.info(s"ready to shutdown the router")
      self ! PoisonPill
      mediator ! Publish(sagaName[S](), SagaRouterStopped(ClusterSagaRouter.sagaName[S]()))
    }
  }
}

class ClusterSagaRouterDispatcher[S <: Saga : ClassTag : SagaHandlersResolution](tenant: String) extends Actor with ActorLogging {
  import ClusterSagaRouter._
  val index = self.path.name.toInt
  
  val address = Serialization.serializedActorPath(self)
  val mediator = DistributedPubSub(context.system).mediator
  val handlersResolution = implicitly[SagaHandlersResolution[S]]

  handlersResolution.resolve() foreach {e =>
    mediator ! Subscribe(withTenantName(tenant, e.getSimpleName), Some(sagaName[S]()), self)
  }

  val sagaRegion = ClusterSharding(context.system).shardRegion(withTenantName(tenant, sagaName[S]()))

  override def receive: Actor.Receive = {
    case SubscribeAck(subscribe) =>
      log.debug(s"received subscribe ack $subscribe")

    case event : DomainEvent =>
      log.debug(s"received event $event!")
      sagaRegion ! event

    case m@InitializeDispatcher(`index`) => {
      sender ! akka.actor.Status.Success(m)
    }
    case _ =>
  }

  override def preRestart(reason: Throwable, message: Option[Any]) = {
    log.debug("SagaRouter restarted")
    super.preRestart(reason, message)
  }

  override def postStop() = {
    super.postStop()
    log.info("SagaRouter stopped")
  }
}


class ClusterSagaRouterGateway[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String, sagaRouterRef: ActorRef) extends Actor with ActorLogging with Stash {
  import ClusterSagaRouter._
  
  val address = Serialization.serializedActorPath(self)
  val mediator = DistributedPubSub(context.system).mediator
  val correlationId = self.path.name
  val sagaRef = context.actorOf(implicitly[SagaFactory[S]].props(correlationId, self), s"${sagaName[S]()}_$correlationId")

  var commandHandlers : Map[Class[_ <: Command], ActorRef] = Map.empty
  sagaRouterRef ! RequestUpdateHandlers
  mediator ! Subscribe(withTenantName(tenant, classOf[UpdateHandlers].getClass.getSimpleName), self)

  context.become(waitUpdateHandlers)

  def waitUpdateHandlers : Actor.Receive = {
    case uh@UpdateHandlers(h) =>
      commandHandlers = h
      context.unbecome()
      unstashAll()
    case _  => stash()
  }

  override def receive: Actor.Receive = {
    case SubscribeAck(subscribe) =>
      log.debug(s"received subscribe ack $subscribe")

    case uh@UpdateHandlers(h) =>
      commandHandlers = h

    case command: Command => {
      log.debug(s"received Command $command")
      commandHandlers.get(command.getClass) match {
        case Some(ref) =>  ref ! command
        case None => log.warning(s"received not handled $command") //TODO: manage commands without handlers
      }
    }

    case event: DomainEvent => {
      log.debug(s"received Event $event")
      sagaRef ! event
    }
  }
  
  override def aroundPostStop() = {
    sagaRef ! PoisonPill
  }
}