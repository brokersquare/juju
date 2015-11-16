package juju.infrastructure.local

import juju.sample.PriorityAggregate.{PriorityCreated, PriorityIncreased}
import juju.testkit.LocalDomainSpec
import juju.testkit.infrastructure.OfficeSpec

class LocalOfficeSpec extends LocalDomainSpec("LocalOffice") with OfficeSpec {
  override protected def subscribeDomainEvents(): Unit = {
    system.eventStream.subscribe(this.testActor, classOf[PriorityCreated])
    system.eventStream.subscribe(this.testActor, classOf[PriorityIncreased])
  }
}