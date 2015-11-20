package juju.infrastructure.cluster
/*
import akka.actor._
import akka.contrib.pattern.ClusterClient.Publish
import akka.contrib.pattern.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import akka.contrib.pattern.{ClusterSharding, DistributedPubSubExtension, ShardRegion}
import akka.serialization.Serialization
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKitBase}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.{AggregateRoot, AggregateRootFactory}
import juju.messages.{Command, DomainEvent}
import juju.sample.PriorityAggregate
import juju.sample.PriorityAggregate.{PriorityIncreased, CreatePriority, PriorityCreated}
import org.scalatest._

import scala.concurrent.duration._
import scala.reflect.ClassTag

/*
import juju.testkit.ClusterDomainSpec
import juju.testkit.infrastructure.OfficeSpec

class ClusterOfficeSpec extends ClusterDomainSpec("ClusterOffice") with OfficeSpec {
  override protected def subscribeDomainEvents(): Unit = {

  }
}*/

object ClusterOfficeSpec {
  def startupNodes(ports: Seq[String]): Map[String, (ActorSystem, ActorRef)] = {
    (ports map { port =>
      // Override the configuration of the port
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
        .withFallback(ConfigFactory.parseString("akka.cluster.seed-nodes=[\"akka.tcp://ClusterSystem@127.0.0.1:2551\"]"))
        //.withFallback(ConfigFactory.parseString("akka.loglevel=\"DEBUG\""))
        .withFallback(ConfigFactory.load("cluster.conf"))
        .withFallback(ConfigFactory.load("domain.conf"))

      // Create an Akka system
      val system = ActorSystem("ClusterSystem", config)
      val office = createOffice[PriorityAggregate](system)

      port -> (system, office)
    }).toMap
  }

  def createOffice[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](system: ActorSystem): ActorRef = {
    val aggregateProps = implicitly[AggregateRootFactory[A]].props
    val resolution = implicitly[AggregateIdResolution[A]]
    val className = implicitly[ClassTag[A]].runtimeClass.asInstanceOf[Class[A]].getSimpleName

    val idExtractor: ShardRegion.IdExtractor = {
      case cmd : Command => (resolution.resolve(cmd), cmd)
      case _ => ???
    }

    val shardResolver: ShardRegion.ShardResolver = {
      case cmd: Command => Integer.toHexString(resolution.resolve(cmd).hashCode).charAt(0).toString
      case _ => ???
    }

    val gatewayProps = Props(classOf[ClusterAggregateGateway], aggregateProps)

    ClusterSharding(system).start(className, Some(gatewayProps), idExtractor, shardResolver)
  }
}
@Ignore
class ClusterOfficeSpec extends {
  //val servers = ClusterOfficeSpec.startupNodes(Seq("2551"))
  val servers = ClusterOfficeSpec.startupNodes(Seq("2551", "0", "0"))
//  val servers = ClusterOfficeSpec.startupNodes(Seq("2551", "2552", "0", "0"))
  implicit val system = servers.head._2._1
} with TestKitBase with FlatSpecLike with Matchers with BeforeAndAfterAll with LazyLogging with TryValues
with DefaultTimeout with ImplicitSender {
  System.setProperty("java.net.preferIPv4Stack", "true")

  implicit override val timeout : Timeout = 5 minutes


  override def afterAll() = {
    servers.map(s=>s._2._1) foreach { s =>
      s shutdown()
      s awaitTermination (60 seconds)
    }
  }

  it should "be able to create the aggregate from the command" in {
    val events = Seq(classOf[PriorityCreated].getSimpleName,classOf[PriorityIncreased].getSimpleName)
    val router = system.actorOf(Props(classOf[EventSubscriberRedirect], this.testActor, events))


    /*
    val mediator = DistributedPubSubExtension(system).mediator
    mediator ! Subscribe(classOf[PriorityCreated].getSimpleName, None, this.testActor)
    expectMsg(SubscribeAck(Subscribe(classOf[PriorityCreated].getSimpleName, None, this.testActor)))
    println(s"subscribed ${classOf[PriorityCreated].getSimpleName}")

    mediator ! Subscribe(classOf[PriorityIncreased].getSimpleName, this.testActor)
    expectMsg(SubscribeAck(Subscribe(classOf[PriorityIncreased].getSimpleName, None, this.testActor)))
*/
    val officeRef = servers.head._2._2

    officeRef ! CreatePriority("giangi")


    expectMsg(timeout.duration, PriorityCreated("giangi"))
  }
}

class EventSubscriberRedirect(destinationActor: ActorRef, eventsToSubscribe: Seq[String]) extends Actor with ActorLogging {
  val mediator = DistributedPubSubExtension(context.system).mediator
  eventsToSubscribe.foreach {
    e => mediator ! Subscribe(e, None, self)
  }
  val address = Serialization.serializedActorPath(self)

  override def receive: Actor.Receive = {
    case SubscribeAck(Subscribe(topic, group, ref)) =>
      log.debug(s"[$address]subscriber ready to receive $topic")

    case event : DomainEvent =>
      destinationActor ! event
      log.info(s"[$address]received $event: going to route")
  }
}

class ClusterAggregateGateway(aggregateProps: Props) extends Actor with ActorLogging {
  val address = Serialization.serializedActorPath(self)
  var aggregateRef : Option[ActorRef] = None
  val mediator = DistributedPubSubExtension(context.system).mediator

  //TODO: set supervisor strategy

  override def receive: Receive = {
    case cmd : Command =>
      val ref  = aggregateRef match {
        case Some(r) => r
        case None =>
          aggregateRef = Some(context.actorOf(aggregateProps, self.path.name))
          log.info(s"[$address]created instance of aggregate ${aggregateRef.get}")
          aggregateRef.get
      }
      ref ! cmd //TODO: do retry in case of timeout???
      log.info(s"[$address]route $cmd to aggregate ${aggregateRef.get}")
    case event : DomainEvent =>
      mediator ! Publish(event.getClass.getSimpleName, event)
      log.info(s"[$address]received $event and published to ${event.getClass.getSimpleName}")
    case e => log.info(s"[$address]detected message $e")
  }
}
*/