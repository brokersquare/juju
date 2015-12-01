package juju.testkit.infrastructure

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
import juju.sample.PriorityAggregate.PriorityIncreased
import juju.testkit.AkkaSpec

import scala.concurrent.duration._
import scala.reflect.ClassTag

trait SagaRouterSpec extends AkkaSpec {
  implicit override val timeout: Timeout = 300 seconds
  protected def createSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String) : ActorRef
  protected def publish(tenant: String, sagaRouterRef : ActorRef, event: DomainEvent)
  protected def shutdownRouter(sagaRouterRef : ActorRef)

  it should "be able to start the saga due to events and receive an emitted command" in {
    val tenant = "1"
    val routerRef = createSagaRouter[PriorityActivitiesSaga](tenant)
    routerRef ! UpdateHandlers(Map.empty + (classOf[ChangeWeight] -> this.testActor))
    expectMsg(timeout.duration, Success)

    publish(tenant, routerRef, PriorityIncreased("x", 1))
    publish(tenant, routerRef, ColorAssigned(1, "red"))

    expectMsgPF(timeout.duration) {
      case ChangeWeight("red", _) =>
    }
    shutdownRouter(routerRef)
  }

  it should "not route domain events depending specific conditions" in {
    val tenant = "2"
    val routerRef = createSagaRouter[PriorityActivitiesSaga](tenant)
    routerRef ! UpdateHandlers(Map.empty + (classOf[ChangeWeight] -> this.testActor))
    expectMsg(timeout.duration, Success)

    publish(tenant, routerRef, PriorityIncreased("x", -1))
    publish(tenant, routerRef, ColorAssigned(-1, "red"))
    expectNoMsg(3 seconds)
    shutdownRouter(routerRef)
  }
  /*

    it should "route wakeup event to all saga if registered" in {
      val routerRef = getSagaRouter[PriorityActivitiesSaga]()
      routerRef ! UpdateHandlers(Map.empty + (classOf[PublishEcho] -> this.testActor))
      expectMsg(timeout.duration, Success)

      publish(routerRef, ColorAssigned(1, "red"))
      publish(routerRef, ColorAssigned(2, "yellow"))

      routerRef ! EchoWakeUp("hello world")

      expectMsgAllOf(timeout.duration,
        PublishEcho("echo from priority 1: hello world"),
        PublishEcho("echo from priority 2: hello world")
      )
      shutdownRouter(routerRef)
    }

    it should "activate saga by activate message" in {
      val routerRef = getSagaRouter[PriorityActivitiesSaga]()

      routerRef ! UpdateHandlers(Map.empty + (classOf[PublishEcho] -> this.testActor))
      expectMsg(timeout.duration, Success)
      routerRef ! PriorityActivitiesActivate("1")
      routerRef ! EchoWakeUp("hello world")

      expectMsgAllOf(timeout.duration, PublishEcho("echo from priority 1: hello world"))
      shutdownRouter(routerRef)
    }*/
}

