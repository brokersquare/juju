package juju.domain

import akka.actor._
import juju.sample.PriorityAggregate
import juju.sample.PriorityAggregate.{PriorityIncreased, IncreasePriority, PriorityCreated, CreatePriority}
import juju.sample.conventions.PersonAggregate
import juju.sample.conventions.PersonAggregate.{WeightChanged, ChangeWeight}
import juju.testkit.LocalDomainSpec

class AggregateRootSpec extends LocalDomainSpec("AggregateRoot") {

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

  it should "be able to handle commands implementing Handle trait" in {
    val fakeRef = system.actorOf(Props(classOf[PersonAggregate]))
    fakeRef ! ChangeWeight("giangi", 80)
    expectMsg(WeightChanged("giangi", 80))
  }
}