package juju.infrastructure

import akka.actor.Status.Success
import akka.actor._
import akka.pattern.gracefulStop
import juju.DomainSpec
import juju.infrastructure.SagaRouter._
import juju.infrastructure.local.LocalEventBus._
import juju.sample.ColorAggregate.ChangeWeight
import juju.sample.ColorPriorityAggregate.ColorAssigned
import juju.sample.PriorityActivitiesSaga
import juju.sample.PriorityActivitiesSaga.{EchoWakeUp, PriorityActivitiesActivate, PublishEcho}
import juju.sample.PriorityAggregate.PriorityIncreased

import scala.concurrent.duration._

class SagaRouterSpec extends DomainSpec("SagaRouter")  {
  it should "be able to start the saga due to events and receive an emitted command" in {
    val routerRef = router[PriorityActivitiesSaga]
    routerRef ! UpdateHandlers(Map.empty + (classOf[ChangeWeight] -> this.testActor))
    expectMsg(Success)

    routerRef ! PriorityIncreased("x", 1)
    routerRef ! ColorAssigned(1, "red")
    expectMsg(3 seconds, ChangeWeight("red", 1))
    gracefulStop(routerRef, 10 seconds)
  }

  it should "not route domain events depending specific conditions" in {
    val routerRef = router[PriorityActivitiesSaga]
    routerRef ! UpdateHandlers(Map.empty + (classOf[ChangeWeight] -> this.testActor))
    expectMsg(Success)
    routerRef ! PriorityIncreased("x", -1)
    routerRef ! ColorAssigned(-1, "red")
    expectNoMsg()
    gracefulStop(routerRef, 10 seconds)
  }


  it should "route wakeup event to all saga if registered" in {
    val routerRef = router[PriorityActivitiesSaga]
    routerRef ! UpdateHandlers(Map.empty + (classOf[PublishEcho] -> this.testActor))
    expectMsg(Success)
    routerRef ! ColorAssigned(1, "red")
    routerRef ! ColorAssigned(2, "yellow")

    routerRef ! EchoWakeUp("hello world")

    expectMsgAllOf(
      PublishEcho("echo from priority 1: hello world"),
      PublishEcho("echo from priority 2: hello world")
    )
    gracefulStop(routerRef, 10 seconds)
  }

  it should "activate saga by activate message" in {
    val routerRef = router[PriorityActivitiesSaga]

    routerRef ! UpdateHandlers(Map.empty + (classOf[PublishEcho] -> this.testActor))
    expectMsg(Success)
    routerRef ! PriorityActivitiesActivate("1")
    routerRef ! EchoWakeUp("hello world")

    expectMsgAllOf(PublishEcho("echo from priority 1: hello world"))
    gracefulStop(routerRef, 10 seconds)
  }
}

