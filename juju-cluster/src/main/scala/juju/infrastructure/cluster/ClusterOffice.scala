package juju.infrastructure.cluster

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.pattern.{ask, pipe}
import akka.serialization.Serialization
import akka.util.Timeout
import juju.domain.AggregateRoot.{CommandReceiveFailure, CommandReceived, AggregateIdResolution}
import juju.domain.{AggregateRoot, AggregateRootFactory}
import juju.infrastructure.Timer.StopTimer
import juju.infrastructure.{EventBus, OfficeFactory, UpdateHandlers}
import juju.messages.{Command, DomainEvent, RouteTo}

import scala.concurrent.Await
import scala.concurrent.duration.{FiniteDuration, _}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object ClusterOffice {
  implicit def clusterOfficeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](_tenant: String)(implicit system : ActorSystem) = {
    new OfficeFactory[A] {
      override val tenant = _tenant

      override def getOrCreate: ActorRef = {
        val actorName = s"$officeName"
        implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
        val retrieveChild = system.actorSelection(system.child(actorName)).resolveOne()

        Try(system.actorOf(Props(new ClusterOfficeProxy[A](tenant, actorName)), actorName)) match {
          case Success(ref) => ref
          case Failure(ex) => Await.ready(retrieveChild, 1 seconds).value.get.get
        }
      }
    }
  }
}

class ClusterOfficeProxy[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](tenant: String, officeName: String) extends Actor with ActorLogging {
  private val system = context.system
  implicit val ec = system.dispatcher
  implicit val timeout = Timeout(context.system.settings.config.getDuration("juju.eventbus.timeout", TimeUnit.SECONDS), TimeUnit.SECONDS)

  val shardRegion = getOrCreate
  val aggregateName = implicitly[ClassTag[A]].runtimeClass.getSimpleName //TODO: take it from an aggregate name service

  var handlers : Map[Class[_ <: Command], ActorRef] = Map.empty

  override def receive: Actor.Receive = {
    case UpdateHandlers(h) =>
      handlers = h
      //log.debug(s"received update handlers => ignore (office cannot route command. Useful only for the saga router)")
      sender ! akka.actor.Status.Success(aggregateName)

    case m : RouteTo =>
      if (m.destinationClass == implicitly[ClassTag[A]].runtimeClass) {
        shardRegion ! m
      } else {
        val actor = handlers.values.filter(_.path.name.endsWith(m.destinationClass.getName)).head
        actor ! m
      }
    case c: Command =>
      val s = sender()
      shardRegion.ask(c)(timeout.duration).pipeTo(s)
    case m =>
      shardRegion ! m
  }

  private def getOrCreate: ActorRef = {
    region.getOrElse {
      startSharding()
      region.get
    }
  }

  private def region: Option[ActorRef] = {
    Try(ClusterSharding(system).shardRegion(officeName)) match {
      case Success(ref) => Some(ref)
      case Failure(ex) if ex.isInstanceOf[IllegalArgumentException] => None
    }
  }

  private def startSharding(): Unit = {
    val aggregateProps = implicitly[AggregateRootFactory[A]].props
    val resolution = implicitly[AggregateIdResolution[A]]

    val idExtractor: ShardRegion.ExtractEntityId = {
      case cmd : Command => (resolution.resolve(cmd), cmd)
      case m : RouteTo => (m.destinationId, m)
    }

    val shardResolver: ShardRegion.ExtractShardId = {
      case cmd: Command => Integer.toHexString(resolution.resolve(cmd).hashCode).charAt(0).toString
      case m: RouteTo => Integer.toHexString(m.destinationId.hashCode).charAt(0).toString
    }

    val officeProps = Props(classOf[ClusterOffice], tenant, aggregateProps, self)

    ClusterSharding(system).start(officeName, officeProps, ClusterShardingSettings(system), idExtractor, shardResolver)
  }
}

class ClusterOffice(tenant: String, aggregateProps: Props, proxy: ActorRef) extends Actor with ActorLogging {
  implicit val timeout = Timeout(context.system.settings.config.getDuration("juju.eventbus.timeout", TimeUnit.SECONDS), TimeUnit.SECONDS)

  val address = Serialization.serializedActorPath(self)
  var aggregateRef : Option[ActorRef] = None
  val mediator = DistributedPubSub(context.system).mediator
  implicit val ec = context.system.dispatcher

  //TODO: set supervisor strategy


  var waitingAcks: Map[ActorRef, (ActorRef, Command)] = Map.empty

  override def receive: Receive = {
    case cmd : Command =>
      val aref = ref
      val s = sender()
      log.debug(s"[$address]routing $cmd to aggregate ${aggregateRef.get}")
      aref ! cmd
      val timer = context.actorOf(juju.infrastructure.Timer.props(timeout.duration))
      context.watch(timer)
      waitingAcks += (timer -> (s,cmd))

    case event : DomainEvent =>
      val subscribedEventName = EventBus.nameWithTenant(tenant, event)
      mediator ! Publish(subscribedEventName, event, sendOneMessageToEachGroup = true)
      log.debug(s"[$address]received $event and published to $subscribedEventName")

    case m:RouteTo =>
      val aref = ref
      if (aref.path.name == m.destinationId) {
        aref ! m
      }
      if (aref.path.name == m.senderId) {
        proxy ! m
      }

    case m@akka.actor.Status.Success(CommandReceived(cmd)) =>
      waitingAcks.find {kv => kv._2._2 == cmd} match {
        case Some((t, (s, _))) =>
          s ! m
          t ! StopTimer
          waitingAcks -= t
        case None =>
      }

    case m@akka.actor.Status.Failure(CommandReceiveFailure(cmd, _)) =>
      waitingAcks.find {kv => kv._2._2 == cmd} match {
        case Some((t, (s, _))) =>
          s ! m
          t ! StopTimer
          waitingAcks -= t
        case None =>
      }

    case Terminated(ref) if waitingAcks.contains(ref) =>
      val (s, cmd) = waitingAcks.get(ref).get
      waitingAcks -= ref
      s ! akka.actor.Status.Failure(new akka.pattern.AskTimeoutException(s"A timeout expired waiting for sending command $cmd"))

    case Terminated(ref) if !waitingAcks.contains(ref) =>

    case e =>
      log.debug(s"[$address]detected message $e")
  }

  private def ref = {
    aggregateRef match {
      case Some(r) => r
      case None =>
        aggregateRef = Some(context.actorOf(aggregateProps, self.path.name))
        log.debug(s"[$address]created instance of aggregate ${aggregateRef.get}")
        aggregateRef.get
    }
  }
}