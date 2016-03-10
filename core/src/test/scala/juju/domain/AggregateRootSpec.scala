package juju.domain

import akka.actor._
import akka.testkit.TestProbe
import juju.domain.AggregateRoot.CommandReceived
import juju.sample.PersonAggregate._
import juju.sample.PriorityAggregate.{CreatePriority, IncreasePriority, PriorityCreated, PriorityIncreased}
import juju.sample.{PersonAggregate, PriorityAggregate}
import juju.testkit.LocalDomainSpec

class AggregateRootSpec extends LocalDomainSpec("AggregateRoot")  {

  it should "be able to send an ack after received" in {
    val probe = TestProbe()
    probe.ignoreNoMsg()

    val aggregate = system.actorOf(Props(classOf[PriorityAggregate]))
    probe.send(aggregate, CreatePriority("giangi"))
    probe.expectMsg(akka.actor.Status.Success(CommandReceived(CreatePriority("giangi"))))
    aggregate ! PoisonPill
  }

  it should "be able to send the first message" in {
    val probe = TestProbe()
    probe.ignoreMsg{case akka.actor.Status.Success(_) => true}

    val aggregate = system.actorOf(Props(classOf[PriorityAggregate]))
    probe.send(aggregate, CreatePriority("giangi"))
    probe.expectMsg(PriorityCreated("giangi"))

    aggregate ! PoisonPill
  }

  it should "be able to send other message" in {
    val probe = TestProbe()
    probe.ignoreMsg{case akka.actor.Status.Success(_) => true}

    val aggregate = system.actorOf(Props(classOf[PriorityAggregate]))
    probe.send(aggregate, CreatePriority("giangi"))
    probe.expectMsg(PriorityCreated("giangi"))
    probe.send(aggregate, IncreasePriority("giangi"))
    probe.expectMsg(PriorityIncreased("giangi", 1))
    probe.send(aggregate, IncreasePriority("giangi"))
    probe.expectMsg(PriorityIncreased("giangi", 2))

    aggregate ! PoisonPill
  }

  it should "be able to handle command by convention" in {
    val probe = TestProbe()
    probe.ignoreMsg{case akka.actor.Status.Success(_) => true}

    val aggregate = system.actorOf(Props(classOf[PersonAggregate]))

    probe.send(aggregate, CreatePerson("giangi"))
    probe.expectMsg(PersonCreated("giangi"))

    probe.send(aggregate, ChangeWeight("giangi", 80))
    probe.expectMsg(WeightChanged("giangi", 80))

    aggregate ! PoisonPill
  }

  it should "be able to handle more commands by convention" in {
    val probe = TestProbe()
    probe.ignoreMsg{case akka.actor.Status.Success(_) => true}

    val aggregate = system.actorOf(Props(classOf[PersonAggregate]))

    probe.send(aggregate, CreatePerson("giangi"))
    probe.expectMsg(PersonCreated("giangi"))

    probe.send(aggregate, ChangeWeight("giangi", 80))
    probe.expectMsg(WeightChanged("giangi", 80))

    probe.send(aggregate, ChangeHeight("giangi", 180))
    probe.expectMsg(HeightChanged("giangi", 180))

    aggregate ! PoisonPill
  }
}