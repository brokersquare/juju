package juju.domain

import akka.actor._
import juju.sample.{PersonAggregate, PriorityAggregate}
import juju.sample.PriorityAggregate.{PriorityIncreased, IncreasePriority, PriorityCreated, CreatePriority}
import juju.sample.PersonAggregate._
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

  it should "be able to handle command by convention" in {
    val fakeRef = system.actorOf(Props(classOf[PersonAggregate]))

    fakeRef ! CreatePerson("giangi")
    expectMsg(PersonCreated("giangi"))

    fakeRef ! ChangeWeight("giangi", 80)
    expectMsg(WeightChanged("giangi", 80))
  }

  it should "be able to handle more commands by convention" in {
    val fakeRef = system.actorOf(Props(classOf[PersonAggregate]))

    fakeRef ! CreatePerson("giangi")
    expectMsg(PersonCreated("giangi"))

    fakeRef ! ChangeWeight("giangi", 80)
    expectMsg(WeightChanged("giangi", 80))

    fakeRef ! ChangeHeight("giangi", 180)
    expectMsg(HeightChanged("giangi", 180))
  }
}