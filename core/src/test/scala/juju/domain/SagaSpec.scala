package juju.domain

import akka.actor._
import akka.pattern.gracefulStop
import juju.sample.AveragePersonWeightSaga.{PublishAverageWeight, PublishWakeUp}
import juju.sample.ColorAggregate.ChangeWeight
import juju.sample.ColorPriorityAggregate.ColorAssigned
import juju.sample.PersonAggregate.WeightChanged
import juju.sample.{AveragePersonWeightSaga, PriorityActivitiesSaga}
import juju.sample.PriorityAggregate.PriorityIncreased
import juju.testkit.LocalDomainSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class SagaSpec extends LocalDomainSpec("Saga") {

  it should "be able to send event to saga without trigger a command" in {
    val sagaRef = system.actorOf(Props(classOf[PriorityActivitiesSaga], 1, this.testActor), s"fakesaga-1")
    sagaRef ! PriorityIncreased("x", 1)
    expectNoMsg(1 second)
  }

  it should "be able to send different event from different aggregate to saga without trigger a command" in {
    val sagaRef = system.actorOf(Props(classOf[PriorityActivitiesSaga], 2, this.testActor), s"fakesaga-2")
    sagaRef ! PriorityIncreased("x", 2)
    sagaRef ! PriorityIncreased("y", 2)
    expectNoMsg(1 second)
  }

  it should "be able to trigger a command after right events" in {
    val sagaRef = system.actorOf(Props(classOf[PriorityActivitiesSaga], 3, this.testActor), s"fakesaga-3")
    sagaRef ! PriorityIncreased("x", 3)
    sagaRef ! ColorAssigned(3, "red")
    expectMsg(3 seconds, ChangeWeight("red", 1))
  }

  it should "not delivery commands during events replay" in {
    val sagaRef = system.actorOf(Props(classOf[PriorityActivitiesSaga], 3, this.testActor), s"fakesaga-4")
    sagaRef ! PriorityIncreased("x", 3)
    sagaRef ! ColorAssigned(3, "red")
    expectMsg(3 seconds, ChangeWeight("red", 1))
    val future = gracefulStop(sagaRef, 2 seconds)
    Await.ready(future, 10 seconds)

    system.actorOf(Props(classOf[PriorityActivitiesSaga], 3, this.testActor), s"fakesaga-4")
    expectNoMsg()
  }

  it should "be able to handle events by apply method" in {
    val sagaRef = system.actorOf(Props(classOf[AveragePersonWeightSaga], "x", this.testActor), s"fakesaga-5")
    sagaRef ! WeightChanged("x", 80)
    expectNoMsg(1 second)
  }

  it should "be able to wakeup by wakeup method" in {
    val sagaRef = system.actorOf(Props(classOf[AveragePersonWeightSaga], "x", this.testActor), s"fakesaga-6")
    sagaRef ! WeightChanged("x", 80)
    sagaRef ! PublishWakeUp()
    expectMsg(3000 seconds, PublishAverageWeight(80))
  }
}