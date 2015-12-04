package juju.infrastructure.cluster

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck, Unsubscribe}
import akka.cluster.sharding.ShardRegion.MessageExtractor
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.serialization.Serialization
import com.typesafe.config.ConfigFactory
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.{Saga, SagaFactory}
import juju.infrastructure.SagaRouter._
import juju.infrastructure.{SagaRouterFactory, UpdateHandlers}
import juju.messages._

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
      getOrCreateRegion(system, s"${routerName}Router", routerProps(tenant), routerMessageExtractor[S]())
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
}

class ClusterSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String) extends Actor with ActorLogging with Stash {
  import ClusterSagaRouter._
  val address = Serialization.serializedActorPath(self)
  val mediator = DistributedPubSub(context.system).mediator
  val sagaName = ClusterSagaRouter.sagaName[S]()

  var commandHandlers : Map[Class[_ <: Command], ActorRef] = Map.empty

  val dispatcherCount = context.system.settings.config.
    withFallback(ConfigFactory.parseString("juju.cluster.dispatcher-count = 1"))
    .getInt("juju.cluster.dispatcher-count")

  val gatewayRegion = ClusterSagaRouter.getOrCreateRegion(context.system, nameWithTenant(tenant, sagaName), ClusterSagaRouter.gatewayProps(tenant, self), ClusterSagaRouter.gatewayMessageExtractor())
  log.info(s"[$tenant]cluster saga router create gateway region $gatewayRegion at $address")
  val dispatcherRegion = ClusterSagaRouter.getOrCreateRegion(context.system, nameWithTenant(tenant, s"${sagaName}Dispatcher"), ClusterSagaRouter.dispatcherProps(tenant), ClusterSagaRouter.dispatcherMessageExtractor())
  log.info(s"[$tenant]cluster saga router create dispatcher region $dispatcherRegion at $address")

  (1 to dispatcherCount).foreach { i =>
    dispatcherRegion ! DispatcherMessage(i, InitializeDispatcher(i))
  }

  context.become(initialize)

  var dispatcherNotInitializedCount = dispatcherCount
  def initialize: Receive = {
    case akka.actor.Status.Success(InitializeDispatcher(index)) =>
      dispatcherNotInitializedCount = dispatcherNotInitializedCount - 1
      if (dispatcherNotInitializedCount == 0) {
        log.info(s"[$tenant]router $sagaName initialization completed. Ready to receive messages")
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
      log.info(s"[$tenant]$sagaName received shutdown. Stopping dispatcher and gateway")
      context.watch(dispatcherRegion)
      dispatcherRegion ! ShardRegion.GracefulShutdown
      context.watch(gatewayRegion)
      gatewayRegion ! ShardRegion.GracefulShutdown
      context.become(Shutdown)
    case wakeup : WakeUp => {
      mediator ! Publish(nameWithTenant(tenant, wakeup.getClass), wakeup)
    }
    case _ =>
  }

  def Shutdown: Receive = {
    case Terminated(`dispatcherRegion`) =>
      log.info(s"[$tenant]$sagaName dispatcher terminated")
      dispatcherTerminated = true
      shutdownIfReady()
    case Terminated(`gatewayRegion`) =>
      log.info(s"[$tenant]$sagaName gateway terminated")
      gatewayTerminated = true
      shutdownIfReady()
    case m => {
      log.debug(s"[$tenant]$sagaName received message $m to stash")
      stash()
    }
  }

  var dispatcherTerminated = false
  var gatewayTerminated = false
  private def shutdownIfReady() = {
    if (dispatcherTerminated && gatewayTerminated) {
      log.info(s"[$tenant]$sagaName ready to shutdown the router")
      self ! PoisonPill
      mediator ! Publish(nameWithTenant(tenant, classOf[SagaRouterStopped]), SagaRouterStopped(sagaName))
    }
  }
}

class ClusterSagaRouterDispatcher[S <: Saga : ClassTag : SagaHandlersResolution](tenant: String) extends Actor with ActorLogging with Stash {
  import ClusterSagaRouter._
  val index = self.path.name.toInt
  val address = Serialization.serializedActorPath(self)
  val mediator = DistributedPubSub(context.system).mediator
  val handlersResolution = implicitly[SagaHandlersResolution[S]]

  log.info(s"[$tenant]cluster saga router dispatcher $index created at $address")

  var subscriptionsAckWaitingList = handlersResolution.resolve() map {e => Subscribe(nameWithTenant(tenant, e.getSimpleName), Some(nameWithTenant(tenant, sagaName[S]())), self)}
 // subscriptionsAckWaitingList foreach {mediator ! _}
  /*
  handlersResolution.resolve() foreach {e =>
    mediator ! Subscribe(nameWithTenant(tenant, e.getSimpleName), Some(nameWithTenant(tenant, sagaName[S]())), self)
  }*/

  val sagaRegionName = nameWithTenant(tenant, sagaName[S]())
  val sagaRegion = ClusterSharding(context.system).shardRegion(sagaRegionName)
  context.become(initializing)

  var initializerRef = ActorRef.noSender
  var initializerMessage : InitializeDispatcher = null
  def initializing : Actor.Receive = {
    case SubscribeAck(subscribe) =>
      log.info(s"[$tenant]saga dispatcher $index received subscribe ack $subscribe")
      subscriptionsAckWaitingList = subscriptionsAckWaitingList.filterNot(_ == subscribe)
      subscriptionsAckWaitingList match {
        case Nil => {
          initializerRef ! akka.actor.Status.Success(initializerMessage)
          context.unbecome()
          unstashAll()
        }
        case _ =>
      }

    case m@InitializeDispatcher(`index`) => {
      subscriptionsAckWaitingList foreach {mediator ! _}
      initializerRef = sender()
      initializerMessage = m
    }
    case _ =>
      stash()
  }

  override def receive: Actor.Receive = {
    case event : DomainEvent =>
      log.info(s"[$tenant]saga dispatcher $index received event $event!")
      sagaRegion ! event
    case _ =>
  }

  override def aroundPostStop() = {
    super.aroundPostStop()
    handlersResolution.resolve() foreach {e =>
      mediator ! Unsubscribe(nameWithTenant(tenant, e.getSimpleName), Some(sagaName[S]()), self)
    }
  }

  override def postStop() = {
    super.postStop()
    log.info("[$tenant]SagaRouter dispatcher stopped")
  }
}


class ClusterSagaRouterGateway[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String, sagaRouterRef: ActorRef) extends Actor with ActorLogging with Stash {
  import ClusterSagaRouter._

  val sagaType = implicitly[ClassTag[S]].runtimeClass.asInstanceOf[Class[S]]
  val address = Serialization.serializedActorPath(self)
  val mediator = DistributedPubSub(context.system).mediator
  val correlationId = self.path.name
  val sagaRef = context.actorOf(implicitly[SagaFactory[S]].props(correlationId, self), s"${sagaName[S]()}_$correlationId")

  log.info(s"[$tenant]cluster saga router gateway $sagaType - $correlationId created at $address")

  var commandHandlers : Map[Class[_ <: Command], ActorRef] = Map.empty
  sagaRouterRef ! RequestUpdateHandlers

  var subscriptionsAckWaitingList =
    implicitly[SagaHandlersResolution[S]].wakeUpBy().map { clazz =>
      Subscribe(nameWithTenant(tenant, clazz), self)
    }.toList.::(Subscribe(nameWithTenant(tenant, classOf[UpdateHandlers]), self))

  subscriptionsAckWaitingList.foreach(s => mediator ! s)
  context.become(waitForSubscribeAck)

  def waitForSubscribeAck: Actor.Receive = {
    case SubscribeAck(s) => 
      subscriptionsAckWaitingList = subscriptionsAckWaitingList.filterNot(_ == s)
      subscriptionsAckWaitingList match {
        case Nil =>
          mediator ! Publish(nameWithTenant(tenant, classOf[SagaIsUp]), SagaIsUp(sagaType, self, tenant, correlationId))
          context.unbecome()
          context.become(waitUpdateHandlers)
          unstashAll()
        case _ =>
      }
    case _ => stash()
  }

  def waitUpdateHandlers: Actor.Receive = {
    case uh@UpdateHandlers(h) =>
      commandHandlers = h
      context.unbecome()
      unstashAll()
    case _  => stash()
  }

  override def receive: Actor.Receive = {
    case SubscribeAck(subscribe) =>
      log.debug(s"[$tenant]received subscribe ack $subscribe")

    case uh@UpdateHandlers(h) =>
      commandHandlers = h

    case command: Command => {
      log.debug(s"[$tenant]received Command $command")
      commandHandlers.get(command.getClass) match {
        case Some(ref) =>  ref ! command
        case None => log.warning(s"[$tenant]received not handled $command") //TODO: manage commands without handlers
      }
    }

    case event: DomainEvent => {
      log.debug(s"[$tenant]received Event $event")
      sagaRef ! event
    }

    case wakeup: WakeUp => {
      sagaRef ! wakeup
    }
  }
  
  override def aroundPostStop() = {
    super.aroundPostStop()
    mediator ! Unsubscribe(nameWithTenant(tenant, classOf[UpdateHandlers]), self)

    implicitly[SagaHandlersResolution[S]].wakeUpBy() foreach { clazz =>
      mediator ! Unsubscribe(nameWithTenant(tenant, clazz), self)
    }
  }

  override def postStop() = {
    super.postStop()
    log.info(s"[$tenant]SagaRouter gateway stopped")
  }
}