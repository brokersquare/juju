package juju.infrastructure.cluster

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberUp}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck, Unsubscribe, UnsubscribeAck}
import juju.infrastructure.{EventBus, HandlersRegistered, RegisterHandlers}
import juju.sample.PriorityAggregate
import juju.sample.PriorityAggregate.CreatePriority
import juju.testkit.{AkkaSpec, ClusterDomainSpec}
//import scala.concurrent.duration._

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

  it should "be able to route commands to the remote bus" in {
    _tenant = "t1"
    val clientSystem = servers.last

    withEventBus { bus =>
      bus ! RegisterHandlers[PriorityAggregate]
      expectMsgPF(timeout.duration) {
        case HandlersRegistered(handlers) =>
      }
      val cluster = Cluster(system)
      cluster.subscribe(testActor, initialStateMode = InitialStateAsEvents, classOf[MemberUp])

      val busproxy = new ClusterCommandProxyFactory(tenant, allowLocalBus = true)(system).actor

      import scala.concurrent.ExecutionContext.Implicits.global
      import scala.concurrent.duration._
      clientSystem.scheduler.schedule(0 seconds, 1 seconds, busproxy, CreatePriority("xyz"))

      val result = fishForMessage(timeout.duration) {
        case akka.actor.Status.Success(CreatePriority("xyz")) =>
          true
        case m: MemberUp =>
          val states = cluster.state.members map (_.status)
          println(s"cluster state before send messages: $states")
          false
        case m =>
          false
      }
      result shouldBe akka.actor.Status.Success(CreatePriority("xyz"))
    }
  }
}
