package juju.testkit.infrastructure

import akka.actor.Status.Success
import akka.actor._
import akka.util.Timeout
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.{Saga, SagaFactory}
import juju.infrastructure.SagaRouter.SagaIsUp
import juju.infrastructure.UpdateHandlers
import juju.messages.DomainEvent
import juju.sample.ColorAggregate.ChangeWeight
import juju.sample.ColorPriorityAggregate.ColorAssigned
import juju.sample.PriorityActivitiesSaga
import juju.sample.PriorityActivitiesSaga.{PriorityActivitiesActivate, EchoWakeUp, PublishEcho}
import juju.sample.PriorityAggregate.PriorityIncreased
import juju.testkit.AkkaSpec

import scala.concurrent.duration._
import scala.reflect.ClassTag

/*
import akka.actor.Status.Success
import akka.actor._
import akka.util.Timeout
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.{Saga, SagaFactory}
import juju.infrastructure.UpdateHandlers
import juju.messages.DomainEvent
import juju.sample.ColorAggregate.ChangeWeight
import juju.sample.ColorPriorityAggregate.ColorAssigned
import juju.sample.PriorityActivitiesSaga
import juju.sample.PriorityActivitiesSaga.{EchoWakeUp, PublishEcho}
import juju.sample.PriorityAggregate.PriorityIncreased
import juju.testkit.AkkaSpec

import scala.concurrent.duration._
import scala.reflect.ClassTag
*/

trait SagaRouterSpec extends AkkaSpec {
  implicit override val timeout: Timeout = 300 seconds
  protected def createSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String) : ActorRef
  protected def publish(tenant: String, sagaRouterRef : ActorRef, event: DomainEvent)
  protected def shutdownRouter[S <: Saga : ClassTag](tenant: String, sagaRouterRef : ActorRef)

  it should "be able to start the saga due to events and receive an emitted command" in {
    val tenant = "T1"
    val routerRef = createSagaRouter[PriorityActivitiesSaga](tenant)
    routerRef ! UpdateHandlers(Map.empty + (classOf[ChangeWeight] -> this.testActor))
    expectMsgType[Success](timeout.duration)

    publish(tenant, routerRef, PriorityIncreased("x", 1))
    publish(tenant, routerRef, ColorAssigned(1, "red"))

    expectMsgType[SagaIsUp](timeout.duration)

    expectMsgPF(timeout.duration) {
      case m@ChangeWeight("red", _) =>
    }

    shutdownRouter[PriorityActivitiesSaga](tenant, routerRef)
  }

  it should "not route domain events depending specific conditions" in {
    val tenant = "T2"
    val routerRef = createSagaRouter[PriorityActivitiesSaga](tenant)
    routerRef ! UpdateHandlers(Map.empty + (classOf[ChangeWeight] -> this.testActor))
    expectMsgType[Success](timeout.duration)

    publish(tenant, routerRef, PriorityIncreased("x", -1)) //-1 doesn't route event
    publish(tenant, routerRef, ColorAssigned(-1, "red"))

    expectNoMsg(3 seconds)
    shutdownRouter[PriorityActivitiesSaga](tenant, routerRef)
  }

  it should "route wakeup event to all saga if registered" in {
    val tenant = "T3"
    val routerRef = createSagaRouter[PriorityActivitiesSaga](tenant)
    routerRef ! UpdateHandlers(Map.empty + (classOf[PublishEcho] -> this.testActor))
    expectMsgType[Success](timeout.duration)

    publish(tenant, routerRef, ColorAssigned(1, "red"))
    publish(tenant, routerRef, ColorAssigned(2, "yellow"))

    expectMsgType[SagaIsUp](timeout.duration)
    expectMsgType[SagaIsUp](timeout.duration)

    routerRef ! EchoWakeUp("hello world")

    expectMsgAllOf(timeout.duration,
      PublishEcho("echo from priority 1: hello world"),
      PublishEcho("echo from priority 2: hello world")
    )
    shutdownRouter[PriorityActivitiesSaga](tenant, routerRef)
  }

  it should "activate saga by activate message" in {
    val tenant = "T4"
    val routerRef = createSagaRouter[PriorityActivitiesSaga](tenant)

    routerRef ! UpdateHandlers(Map.empty + (classOf[PublishEcho] -> this.testActor))
    expectMsgType[Success](timeout.duration)
    routerRef ! PriorityActivitiesActivate("1")

    expectMsgType[SagaIsUp](timeout.duration)
    routerRef ! EchoWakeUp("hello world")

    expectMsgAllOf(timeout.duration, PublishEcho("echo from priority 1: hello world"))
    shutdownRouter[PriorityActivitiesSaga](tenant, routerRef)
  }
}

