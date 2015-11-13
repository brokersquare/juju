package juju.infrastructure

import akka.actor._
import juju.DomainSpec
import juju.infrastructure.Office._
import juju.infrastructure.local.LocalEventBus
import juju.infrastructure.local.LocalEventBus._
import juju.sample.PriorityAggregate
import juju.sample.PriorityAggregate.{CreatePriority, IncreasePriority, PriorityCreated, PriorityIncreased}

import scala.concurrent.duration._

class OfficeSpec extends DomainSpec("Office") {
  it should "be able to create the aggregate from the command" in {
    val officeRef = office[PriorityAggregate]
    system.eventStream.subscribe(this.testActor, classOf[PriorityCreated])
    system.eventStream.subscribe(this.testActor, classOf[PriorityIncreased])

    officeRef ! CreatePriority("giangi")

    expectMsg(3 seconds, PriorityCreated("giangi"))
  }

  it should "be able to route command to existing aggregate" in {
    val officeRef = office[PriorityAggregate]
    system.eventStream.subscribe(this.testActor, classOf[PriorityCreated])
    system.eventStream.subscribe(this.testActor, classOf[PriorityIncreased])

    officeRef ! CreatePriority("giangi")
    expectMsg(3 seconds, PriorityCreated("giangi"))
    officeRef ! IncreasePriority("giangi")
    expectMsg(3 seconds, PriorityIncreased("giangi", 1))
  }

   //TODO: tests not yet implemented
   /*
   it should "be able to route commands from different aggregate ids" in {
     assert(false, "not yet implemented")
   }

   it should  "be able to route commands from different aggregate types" in {
     assert(false, "not yet implemented")
   }

   it should "be able to publish a domain event" in {
     assert(false, "not yet implemented")
   }

   it should "be notified by a domain event" in {
     assert(false, "not yet implemented")
   }
   */
 }