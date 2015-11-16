/*
import akka.actor._
import akka.contrib.pattern.DistributedPubSubMediator.{SubscribeAck, Subscribe, Publish}
import akka.contrib.pattern._
import akka.serialization.Serialization
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import rx.lang.scala.{Observable, Observer, Subscription}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

case class Ping(text: String)
case class Pong(text: String)
case class Echo(id: Int, text: String)

class ClusterSpikeSpec extends WordSpecLike
  with Matchers with BeforeAndAfterAll {

  var servers: Seq[ActorSystem] = null
  override def beforeAll() = {
    System.setProperty("java.net.preferIPv4Stack", "true")
    servers = startupShardNodes(Seq("2551", "2552", "0", "0"))
  }

  override def afterAll() = {
    servers foreach { s =>
      s shutdown()
      s awaitTermination(60 seconds)
      MessageNotifier.clear()
    }
  }

  "after cluster is up" should {
    "could send message to different nodes" in {
      val client = startupClusterClient("0") { (clientRef, count) =>
        val message = Ping(s"[$count]client")
        clientRef ! ClusterClient.Send("/user/sharding/server", message, localAffinity = false)
      }

      val result = MessageNotifier.messages
        .filter(_.isInstanceOf[String])
        .map(_.asInstanceOf[String])
        .distinct
        .take(2)
        .timeout(60 seconds).toBlocking.toList

      result.length should be > 1

      client shutdown()
      client awaitTermination(60 seconds)
    }

    "could publish message inside the cluster" in {
      servers foreach { s =>
        s.actorOf(Props(classOf[EchoSubscriber]),"subscriber")
      }

      val client = startupClusterClient("0") { (clientRef, count) =>
        val message = Echo(count,"client")
        clientRef ! ClusterClient.Send("/user/sharding/server", message, localAffinity = false)
      }

      val result = MessageNotifier.messages
        .filter(_.isInstanceOf[Echo])
        .map(_.asInstanceOf[Echo].text)
        .distinct
        .take(servers.length)
        .timeout(60 seconds).toBlocking.toList

      result should have length servers.length

      client shutdown()
      client awaitTermination(60 seconds)
    }
  }

  val serverConfig =
    """
      |akka {
      |  persistence {
      |    #journal.plugin = "akka.persistence.journal.inmem"
      |    #snapshot-store.plugin = "inmemory-snapshot-store"
      |
      |    journal.plugin = "akka.persistence.inmem.journal"
      |    snapshot-store.plugin = "akka.persistence.inmem.snapshot-store"
      |  }
      |  actor {
      |    provider = "akka.cluster.ClusterActorRefProvider"
      |  }
      |
      |  extensions = ["akka.contrib.pattern.ClusterReceptionistExtension"]
      |  extensions = ["akka.contrib.pattern.DistributedPubSubExtension"]
      |  remote {
      |    log-remote-lifecycle-events = off
      |    netty.tcp {
      |      hostname = "127.0.0.1"
      |      port = 0
      |    }
      |  }
      |
      |  cluster {
      |    seed-nodes = [
      |      "akka.tcp://ClusterSystem@127.0.0.1:2551",
      |      "akka.tcp://ClusterSystem@127.0.0.1:2552"]
      |
      |    auto-down-unreachable-after = 10s
      |  }
      |}
    """.stripMargin

  val clientConfig =
    """
      |akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
      |extensions = ["akka.contrib.pattern.ClusterReceptionistExtension"]
      |extensions = ["akka.contrib.pattern.DistributedPubSubExtension"]
    """.stripMargin

  def idResolver(msg: Any) : String = msg match {
    case msg: Ping => msg.text
    case Echo(id, _) => id.toString
    case _ => ???
  }

  val idExtractor: ShardRegion.IdExtractor = {
    case msg => (idResolver(msg), msg)
  }

  val shardResolver: ShardRegion.ShardResolver = {
    case m => Integer.toHexString(idResolver(m).hashCode).charAt(0).toString
  }

  def startupShardNodes(ports: Seq[String]): Seq[ActorSystem] = {
    ports map { port =>
      // Override the configuration of the port
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
        withFallback(ConfigFactory.parseString(serverConfig))

      // Create an Akka system
      val system = ActorSystem("ClusterSystem", config)

      ClusterSharding(system).start(
        typeName = "server",
        entryProps = Some(Props(classOf[ServerActor])),
        idExtractor = idExtractor,
        shardResolver = shardResolver)

      val region = ClusterSharding(system).shardRegion("server")
      ClusterReceptionistExtension(system)
        .registerService(region)
      system
    }
  }

  def startupClusterClient(port: String)(sendAction: (ActorRef, Int)=>Unit): ActorSystem = {
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port")
      .withFallback(ConfigFactory.parseString(clientConfig))
    val system = ActorSystem("ClientSystem", config)
    val log = system.log

    val initialContacts = Set(
      system.actorSelection("akka.tcp://ClusterSystem@127.0.0.1:2551/user/receptionist"),
      system.actorSelection("akka.tcp://ClusterSystem@127.0.0.1:2552/user/receptionist"))
    val client = system.actorOf(ClusterClient.props(initialContacts))

    var count = 1
    system.scheduler.schedule(5 seconds, 5 seconds) {
      sendAction(client, count)
      count = count + 1
    }
    system
  }
}



class EchoSubscriber extends Actor with ActorLogging {
  val address = Serialization.serializedActorPath(self)
  val mediator = DistributedPubSubExtension(context.system).mediator

  mediator ! Subscribe("echo", self)

  override def receive: Actor.Receive = {
    case SubscribeAck(Subscribe("echo", None, `self`)) =>
      log.debug(s"[$address]echo subscriber ready to receive")
    case m@Echo(id, text) =>
      MessageNotifier.notify(m)
  }
}

class ServerActor extends Actor with ActorLogging {
  val address = Serialization.serializedActorPath(self)
  val mediator = DistributedPubSubExtension(context.system).mediator

  override def receive: Actor.Receive = {
    case m@Ping(text) =>
      MessageNotifier.notify(address)
    case m@Echo(id, text) =>
      mediator ! Publish("echo", Echo(id, address))
    case ReceiveTimeout =>
    case _ =>
  }
}

object MessageNotifier {
  private var observers = Seq.empty[Observer[Object]]

  var messages = createObservable()

  def clear() = {
    observers = Seq.empty
    messages = createObservable()
  }

  def createObservable() = {
    Observable.create[Object](observer => {
      observers = observers :+ observer
      Subscription {
        observer.onCompleted()
        observers = observers.filter (o => o == observer)
        () => observers = observers.filter (o => o != observer)
      }
    })
  }

  def notify(message :Object) = {
    message match {
      case m if m != null => observers foreach {_.onNext(m)}
    }
  }
}
*/