package juju.infrastructure.cluster

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.cluster.sharding.{ClusterShardingSettings, ClusterSharding, ShardRegion}
import akka.serialization.Serialization
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.{AggregateRoot, AggregateRootFactory}
import juju.infrastructure.OfficeFactory
import juju.messages.{DomainEvent, Command}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object ClusterOffice {
  implicit def clusterOfficeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](_tenant: String)(implicit system : ActorSystem) = {
    new OfficeFactory[A] {
      override val tenant = _tenant

      override def getOrCreate: ActorRef = {
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
        //val className = implicitly[ClassTag[A]].runtimeClass.asInstanceOf[Class[A]].getSimpleName

        val idExtractor: ShardRegion.ExtractEntityId = {
          case cmd : Command => (resolution.resolve(cmd), cmd)
          case _ => ???
        }

        val shardResolver: ShardRegion.ExtractShardId = {
          case cmd: Command => Integer.toHexString(resolution.resolve(cmd).hashCode).charAt(0).toString
          case _ => ???
        }

        val officeProps = Props(classOf[ClusterOffice], aggregateProps)

        ClusterSharding(system).start(officeName, officeProps, ClusterShardingSettings(system), idExtractor, shardResolver)
      }
    }
  }
}

class ClusterOffice(aggregateProps: Props) extends Actor with ActorLogging {
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
      mediator ! Publish(event.getClass.getSimpleName, event)
      log.debug(s"[$address]received $event and published to ${event.getClass.getSimpleName}")
    case e => log.debug(s"[$address]detected message $e")
  }
}