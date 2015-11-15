package juju.infrastructure

import juju.sample.PriorityAggregate.{PriorityIncreased, PriorityCreated}
import juju.testkit.infrastructure.OfficeSpec

class LocalOfficeSpec extends OfficeSpec("Local") with UsingLocalEventBus {
  override protected def subscribeDomainEvents(): Unit = {
    system.eventStream.subscribe(this.testActor, classOf[PriorityCreated])
    system.eventStream.subscribe(this.testActor, classOf[PriorityIncreased])
  }
}