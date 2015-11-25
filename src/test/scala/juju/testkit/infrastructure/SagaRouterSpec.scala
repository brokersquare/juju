package juju.testkit.infrastructure

import akka.actor.Status.Success
import akka.actor._
import akka.pattern.gracefulStop
import akka.util.Timeout
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.{Saga, SagaFactory}
import juju.infrastructure.UpdateHandlers
import juju.messages.DomainEvent
import juju.sample.ColorAggregate.ChangeWeight
import juju.sample.ColorPriorityAggregate.ColorAssigned
import juju.sample.PriorityActivitiesSaga
import juju.sample.PriorityActivitiesSaga.{EchoWakeUp, PriorityActivitiesActivate, PublishEcho}
import juju.sample.PriorityAggregate.PriorityIncreased
import juju.testkit.AkkaSpec

import scala.concurrent.duration._
import scala.reflect.ClassTag

trait SagaRouterSpec extends AkkaSpec {
  implicit override val timeout: Timeout = 300 seconds
  protected def getSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory]() : ActorRef
  protected def publish(sagaRouterRef : ActorRef, event: DomainEvent)


  it should "be able to start the saga due to events and receive an emitted command" in {
    val routerRef = getSagaRouter[PriorityActivitiesSaga]()
    routerRef ! UpdateHandlers(Map.empty + (classOf[ChangeWeight] -> this.testActor))
    expectMsg(Success)

    publish(routerRef, PriorityIncreased("x", 1))
    publish(routerRef, ColorAssigned(1, "red"))

    expectMsg(3 seconds, ChangeWeight("red", 1))
    gracefulStop(routerRef, 10 seconds)
  }

  it should "not route domain events depending specific conditions" in {
    val routerRef = getSagaRouter[PriorityActivitiesSaga]()
    routerRef ! UpdateHandlers(Map.empty + (classOf[ChangeWeight] -> this.testActor))
    expectMsg(Success)

    publish(routerRef, PriorityIncreased("x", -1))
    publish(routerRef, ColorAssigned(-1, "red"))
    expectNoMsg()
    gracefulStop(routerRef, 10 seconds)
  }


  it should "route wakeup event to all saga if registered" in {
    val routerRef = getSagaRouter[PriorityActivitiesSaga]()
    routerRef ! UpdateHandlers(Map.empty + (classOf[PublishEcho] -> this.testActor))
    expectMsg(Success)

    publish(routerRef, ColorAssigned(1, "red"))
    publish(routerRef, ColorAssigned(2, "yellow"))

    routerRef ! EchoWakeUp("hello world")

    expectMsgAllOf(
      PublishEcho("echo from priority 1: hello world"),
      PublishEcho("echo from priority 2: hello world")
    )
    gracefulStop(routerRef, 10 seconds)
  }

  it should "activate saga by activate message" in {
    val routerRef = getSagaRouter[PriorityActivitiesSaga]()

    routerRef ! UpdateHandlers(Map.empty + (classOf[PublishEcho] -> this.testActor))
    expectMsg(Success)
    routerRef ! PriorityActivitiesActivate("1")
    routerRef ! EchoWakeUp("hello world")

    expectMsgAllOf(PublishEcho("echo from priority 1: hello world"))
    gracefulStop(routerRef, 10 seconds)
  }
}

