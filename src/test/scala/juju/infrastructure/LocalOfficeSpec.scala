package juju.infrastructure

import juju.infrastructure.local.LocalNode
import juju.sample.PriorityAggregate.{PriorityIncreased, PriorityCreated}
import juju.testkit.infrastructure.OfficeSpec

class LocalOfficeSpec extends OfficeSpec("Local") with LocalNode {
  override protected def subscribeDomainEvents(): Unit = {
    system.eventStream.subscribe(this.testActor, classOf[PriorityCreated])
    system.eventStream.subscribe(this.testActor, classOf[PriorityIncreased])
  }
}