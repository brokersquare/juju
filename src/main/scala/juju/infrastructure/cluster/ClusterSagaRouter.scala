package juju.infrastructure.cluster

import akka.actor._
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.serialization.Serialization
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain._
import juju.infrastructure.SagaRouterFactory
import juju.messages.DomainEvent

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object ClusterSagaRouter {
  implicit def clusterSagaRouterFactory[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](implicit system: ActorSystem) = new SagaRouterFactory[S] {
    private def log = system.log
/*
    override def getOrCreate: ActorRef = {
      val actorName = s"${routerName}Manager"
      implicit val timeout = Timeout(FiniteDuration(10, TimeUnit.SECONDS))
      val props = Props(new ClusterSagaRouterManager[S](routerName))
      //val props = Props(classOf[ClusterSagaRouterManager[S]], this.routerName)
      Try(system.actorOf(props, actorName)) match {
        //TODO: make async
        case Success(ref) =>
          ref
        case Failure(ex) =>
          log.debug(s"fails to create the router manager: try to find as child")
          log.warning(s"${ex.getMessage}")
          val childPath = system.child(actorName)
          log.debug(s"looking for router manager $childPath")
          val childFuture: Future[ActorRef] = system.actorSelection(childPath).resolveOne()
          Await.ready(childFuture, timeout.duration).value.get match {
            case Success(res) => res
            case Failure(ex) =>
              log.warning(s"an error occours ${ex.getMessage}. Retrying to get or create ...")
              getOrCreate
          }
      }
    }
*/

    override def getOrCreate: ActorRef = {
      region.getOrElse {
        startSharding()
        region.get
      }
    }

    private def region: Option[ActorRef] = {
      Try(ClusterSharding(system).shardRegion(routerName)) match {
        case Success(ref) => Some(ref)
        case Failure(ex) if ex.isInstanceOf[IllegalArgumentException] => None
      }
    }

    private def startSharding(): Unit = {
      val sagaFactory = implicitly[SagaFactory[S]]
      val resolution = implicitly[SagaCorrelationIdResolution[S]]
      val className = implicitly[ClassTag[S]].runtimeClass.asInstanceOf[Class[S]].getSimpleName

      val idExtractor: ShardRegion.ExtractEntityId = {
        case event : DomainEvent => (resolution.resolve(event).get, event)
        case _ => ???
      }

      val shardResolver: ShardRegion.ExtractShardId = {
        case event: DomainEvent => Integer.toHexString(resolution.resolve(event).hashCode).charAt(0).toString
        case _ => ???
      }

      val sagaRouterProps = Props(classOf[ClusterSagaRouter], sagaFactory)

      ClusterSharding(system).start(className, sagaRouterProps, ClusterShardingSettings(system), idExtractor, shardResolver)
    }
  }
}
/*
class ClusterSagaRouterManager[S <: Saga : ClassTag :SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](routerName: String) extends Actor with ActorLogging {
  //TODO: setup supervisor strategy (check if cluster guardian is a child of the manager
  private val system = context.system
  private val routerRef = getOrCreateRouter
  private var handlers = Map[Class[_ <: Command], ActorRef]()

  override def receive: Actor.Receive = {
    case UpdateHandlers(h) =>
      log.debug(s"registered UpdateHandlers")
      handlers = h
      sender ! akka.actor.Status.Success

    case cmd : Command =>
      log.debug(s"routing command $cmd to cluster")
      routerRef ! cmd

    case event : DomainEvent =>
      log.debug(s"routing event $event to cluster")
      routerRef ! event

    case m  =>
      log.debug(s"received unexpected message $m")
      ???
  }


  private def getOrCreateRouter: ActorRef = {
    region.getOrElse {
      startSharding()
      region.get
    }
  }

  private def region: Option[ActorRef] = {
    Try(ClusterSharding(system).shardRegion(routerName)) match {
      case Success(ref) => Some(ref)
      case Failure(ex) if ex.isInstanceOf[IllegalArgumentException] => None
    }
  }

  private def startSharding(): Unit = {
    val sagaFactory = implicitly[SagaFactory[S]]
    val resolution = implicitly[SagaCorrelationIdResolution[S]]
    val className = implicitly[ClassTag[S]].runtimeClass.asInstanceOf[Class[S]].getSimpleName

    val idExtractor: ShardRegion.ExtractEntityId = {
      case event : DomainEvent => (resolution.resolve(event).get, event)
      case _ => ???
    }

    val shardResolver: ShardRegion.ExtractShardId = {
      case event: DomainEvent => Integer.toHexString(resolution.resolve(event).hashCode).charAt(0).toString
      case _ => ???
    }

    val sagaRouterProps = Props(classOf[ClusterSagaRouter], sagaFactory)

    ClusterSharding(system).start(className, sagaRouterProps, ClusterShardingSettings(system), idExtractor, shardResolver)
  }
}
*/

class ClusterSagaRouter(sagaFactory: SagaFactory[_]) extends Actor with ActorLogging {
  val address = Serialization.serializedActorPath(self)
  var aggregateRef : Option[ActorRef] = None


  override def receive: Receive = {
    case event: DomainEvent => log.debug(s"cluster saga router received event $event")
    case m =>
      log.debug(s"cluster saga router received message $m")
      ???
  }
}