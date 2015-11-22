package juju.infrastructure.cluster

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.serialization.Serialization
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKitBase}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.{AggregateRoot, AggregateRootFactory}
import juju.messages.{Command, DomainEvent}
import juju.sample.PriorityAggregate
import juju.sample.PriorityAggregate.{IncreasePriority, CreatePriority, PriorityCreated, PriorityIncreased}
import org.scalatest._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.Random

/*
import juju.testkit.ClusterDomainSpec
import juju.testkit.infrastructure.OfficeSpec

class ClusterOfficeSpec extends ClusterDomainSpec("ClusterOffice") with OfficeSpec {
  override protected def subscribeDomainEvents(): Unit = {

  }
}*/

object ClusterOfficeSpec {
  def startupNodes(seedPort: String, ports: Seq[String]): Map[String, (ActorSystem, ActorRef)] = {
    ((Seq(seedPort) ++ ports).toSet[String] map { port =>
      // Override the configuration of the port
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
        .withFallback(ConfigFactory.parseString("akka.cluster.seed-nodes=[\"akka.tcp://ClusterSystem@127.0.0.1:" + seedPort + "\"]"))
        .withFallback(ConfigFactory.parseResourcesAnySyntax("domain.conf"))
        .withFallback(ConfigFactory.parseResourcesAnySyntax("cluster.conf"))

//      println(s"akka.persistence.journal.plugin: ${config.getString("akka.persistence.journal.plugin").toString}")
//      println(s"akka.cluster.auto-down-unreachable-after:${config.getDuration("akka.cluster.auto-down-unreachable-after").toString}")

      val system = ActorSystem("ClusterSystem", config)
      val office = createOffice[PriorityAggregate](system)

      port -> (system, office)
    }).toMap
  }

  def createOffice[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](system: ActorSystem): ActorRef = {
    val aggregateProps = implicitly[AggregateRootFactory[A]].props
    val resolution = implicitly[AggregateIdResolution[A]]
    val className = implicitly[ClassTag[A]].runtimeClass.asInstanceOf[Class[A]].getSimpleName

    val idExtractor: ShardRegion.ExtractEntityId = {
      case cmd : Command => (resolution.resolve(cmd), cmd)
      case _ => ???
    }

    val shardResolver: ShardRegion.ExtractShardId = {
      case cmd: Command => Integer.toHexString(resolution.resolve(cmd).hashCode).charAt(0).toString
      case _ => ???
    }

    val gatewayProps = Props(classOf[ClusterAggregateGateway], aggregateProps)

    ClusterSharding(system).start(className, gatewayProps, ClusterShardingSettings(system), idExtractor, shardResolver)
  }
}

class ClusterOfficeSpec extends {
  val seedPort: Int = Random.shuffle(2551 to 2600).toSet.head
  val servers = ClusterOfficeSpec.startupNodes(seedPort.toString, Seq("0", "0"))
  implicit val system = servers.head._2._1
} with TestKitBase with FlatSpecLike with Matchers with BeforeAndAfterAll with LazyLogging with TryValues
with DefaultTimeout with ImplicitSender {
  System.setProperty("java.net.preferIPv4Stack", "true")

  implicit override val timeout : Timeout = 5 seconds

  override def afterAll() = {
    servers.map(s=>s._2._1) foreach { s =>
      s.terminate()
      Await.result(s.whenTerminated, 10 seconds)
    }
  }

  it should "be able to create the aggregate from the command" in {
    subscribeDomainEvents()
    val officeRef = servers.head._2._2

    officeRef ! CreatePriority("giangi")
    expectMsg(timeout.duration, PriorityCreated("giangi"))
  }


  it should "be able to route command to existing aggregate" in {
    subscribeDomainEvents()

    val officeRef = servers.head._2._2

    officeRef ! CreatePriority("giangi")
    expectMsg(timeout.duration, PriorityCreated("giangi"))
    officeRef ! IncreasePriority("giangi")
    expectMsg(timeout.duration, PriorityIncreased("giangi", 1))
  }


  def subscribeDomainEvents() = {
    val events = Seq(classOf[PriorityCreated].getSimpleName,classOf[PriorityIncreased].getSimpleName)

    val mediator = DistributedPubSub(system).mediator
    events.foreach { e =>
      mediator ! Subscribe(e, None, this.testActor)
      expectMsg(SubscribeAck(Subscribe(e, None, this.testActor)))
    }
  }
}

class ClusterAggregateGateway(aggregateProps: Props) extends Actor with ActorLogging {
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
