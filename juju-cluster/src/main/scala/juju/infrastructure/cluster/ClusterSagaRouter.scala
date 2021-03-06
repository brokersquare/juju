package juju.infrastructure.cluster

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck, Unsubscribe}
import akka.cluster.sharding.ShardRegion.MessageExtractor
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.serialization.Serialization
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.{Saga, SagaFactory}
import juju.infrastructure.SagaRouter._
import juju.infrastructure._
import juju.messages._

import scala.concurrent.Await
import scala.concurrent.duration.{FiniteDuration, _}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object ClusterSagaRouter {
  case class DispatcherMessage(index: Int, message: Message) extends InfrastructureMessage
  case object RequestUpdateHandlers extends InfrastructureMessage
  case object ShutdownSagaRouter extends InfrastructureMessage
  case class SagaRouterStopped(sagaType: String) extends InfrastructureMessage
  case class InitializeDispatcher(index: Int) extends InfrastructureMessage
  case class BindToAllDomainEvent(event: DomainEvent) extends InfrastructureMessage

  implicit def clusterSagaRouterFactory[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](_tenant: String)(implicit system: ActorSystem) = new SagaRouterFactory[S] {
    override val tenant :String = _tenant

    override def getOrCreate: ActorRef = {
      val actorName = {
        routerName()
      }
      implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
      val retrieveChild = system.actorSelection(system.child(actorName)).resolveOne()

      Try(system.actorOf(Props(new ClusterSagaProxy[S](tenant, actorName)), actorName)) match {
        case Success(ref) => ref
        case Failure(ex) => Await.ready(retrieveChild, 1 seconds).value.get.get
      }
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
  def dispatcherProps[S <: Saga : ClassTag: SagaHandlersResolution : SagaCorrelationIdResolution](tenant: String) = Props(new ClusterSagaRouterDispatcher[S](tenant))
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
      case activate : Activate  => activate.correlationId
    }
    override def shardId(message: Any): String = message match {
      case event: DomainEvent => Integer.toHexString(correlationIdResolution.resolve(event).hashCode).charAt(0).toString
      case activate: Activate => Integer.toHexString(activate.correlationId.hashCode).charAt(0).toString
    }
    override def entityMessage(message: Any): Any = message match {
      case e: DomainEvent => e
      case e: Activate => e
    }
  }
}

class ClusterSagaProxy[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String, routerName: String) extends Actor with ActorLogging with Stash {
  import ClusterSagaRouter._
  import akka.pattern.{ask, pipe}

  private val system = context.system
  implicit val ec = system.dispatcher
  implicit val timeout = Timeout(context.system.settings.config.getDuration("juju.eventbus.timeout", TimeUnit.SECONDS), TimeUnit.SECONDS)

  var handlers = Map[Class[_ <: Command], ActorRef]()
  val subscribedEvents = implicitly[SagaHandlersResolution[S]].resolve()
  val region = getOrCreateRegion(system, s"${routerName}Router", routerProps(tenant), routerMessageExtractor[S]())

  var updateHandlersSender: Option[ActorRef] = None

  override def receive: Actor.Receive = {
    case e@GetSubscribedDomainEvents =>
      sender ! DomainEventsSubscribed(subscribedEvents)
    case UpdateHandlers(h) =>
      updateHandlersSender = Some(sender())
      region ! UpdateHandlers(h)
      context.become(handlerUpdating)
    case m =>
      val s = sender()
      region.ask(m)(timeout.duration).pipeTo(s)
  }

  def handlerUpdating: Actor.Receive = {
    case m: akka.actor.Status.Success =>
      updateHandlersSender.getOrElse(ActorRef.noSender) ! m
      updateHandlersSender = None
      context.unbecome()
      unstashAll()
    case m =>
      stash()
  }
}

class ClusterSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String) extends Actor with ActorLogging with Stash {
  import ClusterSagaRouter._
  import EventBus._
  import akka.pattern.ask

  private val system = context.system
  implicit val ec = system.dispatcher
  implicit val timeout = Timeout(context.system.settings.config.getDuration("juju.eventbus.timeout", TimeUnit.SECONDS), TimeUnit.SECONDS)

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
      sender ! akka.actor.Status.Success(uh)
    case ShutdownSagaRouter =>
      log.info(s"[$tenant]$sagaName received shutdown. Stopping dispatcher and gateway")
      context.watch(dispatcherRegion)
      dispatcherRegion ! ShardRegion.GracefulShutdown
      context.watch(gatewayRegion)
      gatewayRegion ! ShardRegion.GracefulShutdown
      context.become(Shutdown)
    case wakeup : WakeUp =>
      val topic = nameWithTenant(tenant, wakeup.getClass)
      mediator ! Publish(topic, wakeup)
      sender() ! akka.actor.Status.Success(wakeup)
    case activate : Activate =>
      val s = sender()
      gatewayRegion.ask(activate)(timeout.duration).onComplete {
        case scala.util.Success(_) => s ! akka.actor.Status.Success(activate)
        case scala.util.Failure(cause) => s ! akka.actor.Status.Failure(cause)
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

class ClusterSagaRouterDispatcher[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution](tenant: String) extends Actor with ActorLogging with Stash {
  import ClusterSagaRouter._
  import EventBus._

  val index = self.path.name.toInt
  val address = Serialization.serializedActorPath(self)
  val mediator = DistributedPubSub(context.system).mediator
  val handlersResolution = implicitly[SagaHandlersResolution[S]]
  val correlationIdResolution = implicitly[SagaCorrelationIdResolution[S]]

  log.info(s"[$tenant]cluster saga router dispatcher $index created at $address")
  val subscriptionGroup = sagaName[S]()
  var subscriptionsAckWaitingList = handlersResolution.resolve() map {e => Subscribe(nameWithTenant(tenant, e.getSimpleName), Some(nameWithTenant(tenant, subscriptionGroup)), self)}

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
    case event : DomainEvent if correlationIdResolution.resolve(event).matchOne =>
      log.debug(s"[$tenant]saga dispatcher $index received event $event!")
      sagaRegion ! event

    case event : DomainEvent if correlationIdResolution.resolve(event).matchAll =>
      val topic = EventBus.nameWithTenant(tenant, "bindAll_" + event.getClass.getSimpleName)
      log.debug(s"[$tenant]saga dispatcher $index received event $event and publish to topic $topic")
      mediator ! Publish(topic, BindToAllDomainEvent(event))
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


class ClusterSagaRouterGateway[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String, sagaRouter: ActorRef) extends Actor with ActorLogging with Stash {
  import ClusterSagaRouter._
  import EventBus._

  val sagaType = implicitly[ClassTag[S]].runtimeClass.asInstanceOf[Class[S]]
  val address = Serialization.serializedActorPath(self)
  val mediator = DistributedPubSub(context.system).mediator
  val correlationId = self.path.name
  val saga = context.actorOf(implicitly[SagaFactory[S]].props(correlationId, self), s"${sagaName[S]()}_$correlationId")

  log.info(s"[$tenant]cluster saga router gateway $sagaType - $correlationId created at $address")

  var commandHandlers : Map[Class[_ <: Command], ActorRef] = Map.empty
  sagaRouter ! RequestUpdateHandlers

  val handlersResolution = implicitly[SagaHandlersResolution[S]]

  var wakeUpsSubscriptionsAckWaitingList =
    handlersResolution.wakeUpBy().map { clazz =>
      Subscribe(nameWithTenant(tenant, clazz), self)
    }.toList.::(Subscribe(nameWithTenant(tenant, classOf[UpdateHandlers]), self))

  var bindAllAckWaitingList = handlersResolution.resolve() map {e =>
    val topic = nameWithTenant(tenant, "bindAll_" + e.getSimpleName)
    Subscribe(topic, None, self)
  }

  (wakeUpsSubscriptionsAckWaitingList ++ bindAllAckWaitingList).foreach(s => mediator ! s)
  context.become(waitForSubscribeAck)

  def waitForSubscribeAck: Actor.Receive = {
    case SubscribeAck(s) =>
      log.debug(s"[$tenant]cluster saga router gateway $sagaType - $correlationId received subscribtion $s")
      bindAllAckWaitingList = bindAllAckWaitingList.filterNot(_ == s)
      wakeUpsSubscriptionsAckWaitingList = wakeUpsSubscriptionsAckWaitingList.filterNot(_ == s)
      wakeUpsSubscriptionsAckWaitingList match {
        case Nil if bindAllAckWaitingList.isEmpty =>
          log.info(s"[$tenant]cluster saga router gateway $sagaType - $correlationId subscription completed")
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

    case activate: Activate =>
      log.debug(s"received $activate message")
      sender() ! akka.actor.Status.Success(activate)

    case event: DomainEvent => {
      log.debug(s"[$tenant]received Event $event")
      saga ! event
    }

    case BindToAllDomainEvent(event) => {
      log.debug(s"[$tenant]received Event $event bound to all")
      saga ! event
    }

    case wakeup: WakeUp => {
      saga ! wakeup
    }
  }
  
  override def aroundPostStop() = {
    super.aroundPostStop()
    mediator ! Unsubscribe(nameWithTenant(tenant, classOf[UpdateHandlers]), self)

    implicitly[SagaHandlersResolution[S]].wakeUpBy() foreach { clazz =>
      mediator ! Unsubscribe(nameWithTenant(tenant, clazz), self)
    }

    implicitly[SagaHandlersResolution[S]].resolve() foreach { clazz =>
      val topic = nameWithTenant(tenant, "bindAll_" + clazz.getSimpleName)
      mediator ! Unsubscribe(topic, None, self)
    }
  }

  override def postStop() = {
    super.postStop()
    log.info(s"[$tenant]SagaRouter gateway stopped")
  }
}