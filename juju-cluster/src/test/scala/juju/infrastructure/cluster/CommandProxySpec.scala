package juju.infrastructure.cluster

import akka.actor._
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberUp}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck, Unsubscribe, UnsubscribeAck}
import akka.cluster.routing.{ClusterRouterGroup, ClusterRouterGroupSettings}
import akka.cluster.{Cluster, MemberStatus}
import akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope
import akka.routing.{ConsistentHashingGroup, RoundRobinGroup}
import juju.infrastructure.EventBus
import juju.sample.PriorityAggregate.{CreatePriority, PriorityCreated}
import juju.testkit.{AkkaSpec, ClusterDomainSpec}


class CommandProxySpec extends ClusterDomainSpec("CommandProxy", ClusterDomainSpec.clusterConfig, Seq("0", "0")) with AkkaSpec {
  var _tenant = ""
  override def tenant = _tenant

  override def withEventBus(subscribedEvents : Seq[Class[_]])(action : ActorRef => Unit) = {
    val events = subscribedEvents map(_.getSimpleName)
    var busRef: ActorRef = null
    val mediator = DistributedPubSub(system).mediator

    try {
      busRef = system.actorOf(EventBus.props(tenant),EventBus.nameWithTenant(tenant, "Bus"))
      val subscriptionGroup = "testGroup"
      events.foreach { e =>
        mediator ! Subscribe(EventBus.nameWithTenant(tenant, e), Some(EventBus.nameWithTenant(tenant, subscriptionGroup)), this.testActor)
        expectMsg(SubscribeAck(Subscribe(EventBus.nameWithTenant(tenant, e), Some(EventBus.nameWithTenant(tenant, subscriptionGroup)), this.testActor)))
      }

      action(busRef)
    } finally {
      events.foreach { e =>
        mediator ! Unsubscribe(EventBus.nameWithTenant(tenant, e), None, this.testActor)
        expectMsg(UnsubscribeAck(Unsubscribe(EventBus.nameWithTenant(tenant, e), None, this.testActor)))
      }

      if (busRef != null) busRef ! PoisonPill
    }


  }

  it should "be able to route messages" in {
    _tenant = "t1"
    val clientSystem = servers.last
    println(s"client system address ${address(clientSystem)}")

    waitUntilAllClusterMembersAreUp()

    var routee1 = system.actorOf(Props(new ClusterRoutee(testActor)), "bus")

    val routeesPaths = s"/user/bus*" :: Nil
    val busproxyConf = ClusterRouterGroup(
      ConsistentHashingGroup(routeesPaths),
      ClusterRouterGroupSettings(
        totalInstances = 100,
        routeesPaths = routeesPaths,
        allowLocalRoutees = false,
        useRole = None
      )
    )

    val busproxy = clientSystem.actorOf(busproxyConf.props(), "busproxy")

    val word = "xyz"
    var state = Cluster(clientSystem).state
    busproxy ! ConsistentHashableEnvelope(CreatePriority(word), word)

    val result = fishForMessage(timeout.duration) {
      case m : PriorityCreated =>
        println(s"received $m")
        true
      case m =>
        println(s"discarded message $m")
        false
    }

    result shouldBe PriorityCreated(word)
  }

  def waitUntilAllClusterMembersAreUp() = {
    val cluster = Cluster(system)
    cluster.subscribe(testActor, initialStateMode = InitialStateAsEvents, classOf[MemberUp])
    receiveWhile(timeout.duration) {
      case MemberUp(member) if !cluster.state.members.forall(_.status == MemberStatus.Up) =>
        println(s"-MemberUp- $member")
        println(s"${cluster.state.members}")
    }
    cluster.unsubscribe(testActor)

    val state = cluster.state
    state.members.forall(_.status == MemberStatus.Up) shouldBe true
    ignoreMsg {
      case _ : MemberUp =>
        true
      case _  =>
        false
    }
    state
  }

  def address(asystem: ActorSystem) = {
    Cluster(asystem).selfAddress
  }

  case class ClusterReply(any: Any)
  class ClusterRoutee(tester: ActorRef) extends Actor with ActorLogging {
    def receive = {
      case CreatePriority(name) =>
        tester ! PriorityCreated(name)
      case m =>
        println(s"message $m received by ${context.self.path}")
        tester ! ClusterReply(m)
    }
  }

/*
  it should "be able to route commands to the remote bus" in {
    _tenant = "t1"
    val clientSystem = servers.last

    withEventBus { bus =>
      /*bus ! RegisterHandlers[PriorityAggregate]
      expectMsgPF(timeout.duration) {
        case HandlersRegistered(handlers) =>
      }*/

      Cluster(system).subscribe(testActor, classOf[MemberUp])
      expectMsgClass(classOf[CurrentClusterState])
      //expectMsgClass(classOf[MemberUp])
      Cluster(system).unsubscribe(testActor, classOf[MemberUp])

      //val busproxy = new ClusterCommandProxyFactory(tenant)(clientSystem).actor

      val routeesPaths = s"/user/${EventBus.actorName(tenant)}*" :: Nil

      val busproxyConf = ClusterRouterGroup(
        ConsistentHashingGroup(Nil),
        ClusterRouterGroupSettings(
          totalInstances = 100,
          routeesPaths = routeesPaths,
          allowLocalRoutees = false,
          useRole = None
        )
      )
      val busproxy = clientSystem.actorOf(busproxyConf.props(), "busproxy")
      //val busproxy = clientSystem.actorOf(new RoundRobinGroup(routeePaths).props(), "busproxy")

      //busproxy ! ConsistentHashableEnvelope(CreatePriority("xyz"), "abc")
      busproxy ! ConsistentHashableEnvelope("xyz", "abc")
      expectMsg(timeout.duration, PriorityCreated("xyz"))
      /*
      expectMsgPF(timeout.duration) {
        case e@HandlersRegistered(_) => println(s"message  HandlersRegistered retrieved $e")
      }*/
    }
  }*/
}


trait CommandProxyFactory {
  def actor : ActorRef
}

class ClusterCommandProxyFactory(tenant : String = "")(implicit system: ActorSystem) extends CommandProxyFactory {
  override def actor: ActorRef = {
    val routeePaths = List(s"/user/${EventBus.actorName(tenant)}*")
    val proxyName = EventBus.nameWithTenant(tenant,"busproxy")
    system.actorOf(new RoundRobinGroup(routeePaths).props(), "busproxy")
  }
}