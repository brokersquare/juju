package juju.domain

import akka.actor._
import juju.DomainSpec
import juju.sample.PriorityAggregate
import juju.sample.PriorityAggregate.{PriorityIncreased, IncreasePriority, PriorityCreated, CreatePriority}

class AggregateRootSpec extends DomainSpec("AggregateRoot") {

  it should "be able to send the first  message" in {
    val fakeRef = system.actorOf(Props(classOf[PriorityAggregate]))
    fakeRef ! CreatePriority("giangi")
    expectMsg(PriorityCreated("giangi"))
  }

  it should "be able to send other message" in {
    val fakeRef = system.actorOf(Props(classOf[PriorityAggregate]))
    fakeRef ! CreatePriority("giangi")
    expectMsg(PriorityCreated("giangi"))
    fakeRef ! IncreasePriority("giangi")
    expectMsg(PriorityIncreased("giangi", 1))
    fakeRef ! IncreasePriority("giangi")
    expectMsg(PriorityIncreased("giangi", 2))
  }
}