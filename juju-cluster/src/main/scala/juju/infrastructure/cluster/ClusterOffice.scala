package juju.infrastructure.cluster

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.cluster.sharding.{ClusterShardingSettings, ClusterSharding, ShardRegion}
import akka.serialization.Serialization
import akka.util.Timeout
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.{AggregateRoot, AggregateRootFactory}
import juju.infrastructure.{EventBus, UpdateHandlers, OfficeFactory}
import juju.messages.{Command, DomainEvent}

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration._

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
  val shardRegion = getOrCreate
  val aggregateName = implicitly[ClassTag[A]].runtimeClass.getSimpleName //TODO: take it from an aggregate name service

  override def receive: Actor.Receive = {
    case UpdateHandlers(_) =>
      //log.debug(s"received update handlers => ignore (office cannot route command. Useful only for the saga router)")
      sender ! akka.actor.Status.Success(aggregateName)

    case m => shardRegion ! m
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
      case _ => ???
    }

    val shardResolver: ShardRegion.ExtractShardId = {
      case cmd: Command => Integer.toHexString(resolution.resolve(cmd).hashCode).charAt(0).toString
      case _ => ???
    }

    val officeProps = Props(classOf[ClusterOffice], tenant, aggregateProps)

    ClusterSharding(system).start(officeName, officeProps, ClusterShardingSettings(system), idExtractor, shardResolver)
  }
}

class ClusterOffice(tenant: String, aggregateProps: Props) extends Actor with ActorLogging {
  val address = Serialization.serializedActorPath(self)
  var aggregateRef : Option[ActorRef] = None
  val mediator = DistributedPubSub(context.system).mediator

  //TODO: set supervisor strategy

  override def receive: Receive = {
    case cmd : Command =>
      val ref  = aggregateRef match {
        case Some(r) => r
        case None =>
          aggregateRef = Some(context.actorOf(aggregateProps, self.path.name))
          log.debug(s"[$address]created instance of aggregate ${aggregateRef.get}")
          aggregateRef.get
      }
      ref ! cmd //TODO: do retry in case of timeout???
      log.debug(s"[$address]route $cmd to aggregate ${aggregateRef.get}")
    case event : DomainEvent =>
      val subscribedEventName = EventBus.nameWithTenant(tenant, event)
      mediator ! Publish(subscribedEventName, event, sendOneMessageToEachGroup = true)
      log.debug(s"[$address]received $event and published to $subscribedEventName")
    case e => log.debug(s"[$address]detected message $e")
  }
}