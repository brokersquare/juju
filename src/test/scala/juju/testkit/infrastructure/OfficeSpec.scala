package juju.testkit.infrastructure

import akka.actor._
import akka.util.Timeout
import juju.domain.{AggregateRootFactory, AggregateRoot}
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.sample.PriorityAggregate
import juju.sample.PriorityAggregate.{CreatePriority, IncreasePriority, PriorityCreated, PriorityIncreased}
import juju.testkit.AkkaSpec

import scala.concurrent.duration._
import scala.reflect.ClassTag

trait OfficeSpec extends AkkaSpec {
  implicit override val timeout: Timeout = 300 seconds
  protected def subscribeDomainEvents()
  protected def getOffice[A <: AggregateRoot[_]: AggregateIdResolution : AggregateRootFactory : ClassTag]() : ActorRef

  it should "be able to create the aggregate from the command" in {
    subscribeDomainEvents()

    val officeRef = getOffice[PriorityAggregate]()
    officeRef ! CreatePriority("giangi")

    expectMsg(timeout.duration, PriorityCreated("giangi"))
  }

  it should "be able to route command to existing aggregate" in {
    subscribeDomainEvents()

    val officeRef = getOffice[PriorityAggregate]()

    officeRef ! CreatePriority("giangi")
    expectMsg(timeout.duration, PriorityCreated("giangi"))
    officeRef ! IncreasePriority("giangi")
    expectMsg(timeout.duration, PriorityIncreased("giangi", 1))
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