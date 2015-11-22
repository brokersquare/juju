package juju.infrastructure.cluster

import akka.actor.ActorRef
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.{AggregateRoot, AggregateRootFactory}
import juju.sample.PriorityAggregate
import juju.sample.PriorityAggregate.{PriorityCreated, PriorityIncreased}
import juju.testkit.ClusterDomainSpec
import juju.testkit.infrastructure.OfficeSpec

import scala.reflect.ClassTag

class ClusterOfficeSpec extends ClusterDomainSpec("ClusterOffice") with OfficeSpec {
  val offices = servers.map {
    ClusterDomainSpec.createOffice[PriorityAggregate]
  }

  override protected def subscribeDomainEvents(): Unit = {
    val events = Seq(classOf[PriorityCreated].getSimpleName,classOf[PriorityIncreased].getSimpleName)

    val mediator = DistributedPubSub(system).mediator
    events.foreach { e =>
      mediator ! Subscribe(e, None, this.testActor)
      expectMsg(SubscribeAck(Subscribe(e, None, this.testActor)))
    }
  }
  override protected def getOffice[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](): ActorRef = this.officeFactory.getOrCreate
}