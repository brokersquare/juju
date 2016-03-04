package juju.infrastructure.kafka

//TODO: run more test suites using TestServer crash the test on second suite
/*
import akka.actor.ActorRef
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.{AggregateRoot, AggregateRootFactory}
import juju.infrastructure.local.LocalOffice
import juju.sample.PriorityAggregate.{PriorityCreated, PriorityIncreased}
import juju.testkit.KafkaDomainSpec
import juju.testkit.infrastructure.OfficeSpec

import scala.reflect.ClassTag

class KafkaOfficeSpec extends KafkaDomainSpec("KafkaOffice", 2381) with OfficeSpec {
  override protected def subscribeDomainEvents(): Unit = {
    system.eventStream.subscribe(this.testActor, classOf[PriorityCreated])
    system.eventStream.subscribe(this.testActor, classOf[PriorityIncreased])
  }

  override protected def createOffice[A <: AggregateRoot[_]: AggregateIdResolution : AggregateRootFactory : ClassTag](tenant: String): ActorRef = LocalOffice.localOfficeFactory(tenant).getOrCreate
}
*/