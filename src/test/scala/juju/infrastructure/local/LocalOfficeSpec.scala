package juju.infrastructure.local

import akka.actor.ActorRef
import juju.domain.{AggregateRootFactory, AggregateRoot}
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.sample.PriorityAggregate.{PriorityCreated, PriorityIncreased}
import juju.testkit.LocalDomainSpec
import juju.testkit.infrastructure.OfficeSpec

import scala.reflect.ClassTag

class LocalOfficeSpec extends LocalDomainSpec("LocalOffice") with OfficeSpec {
  override protected def subscribeDomainEvents(): Unit = {
    system.eventStream.subscribe(this.testActor, classOf[PriorityCreated])
    system.eventStream.subscribe(this.testActor, classOf[PriorityIncreased])
  }

  override protected def getOffice[A <: AggregateRoot[_]: AggregateIdResolution : AggregateRootFactory : ClassTag](): ActorRef = officeFactory[A].getOrCreate
}