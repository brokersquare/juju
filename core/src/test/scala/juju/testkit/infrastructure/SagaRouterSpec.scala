package juju.testkit.infrastructure

import akka.actor.Status.Success
import akka.actor._
import akka.testkit.TestProbe
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.resolvers.ByConventions
import juju.domain.{Saga, SagaFactory}
import juju.infrastructure.SagaRouter.SagaIsUp
import juju.infrastructure.UpdateHandlers
import juju.messages.DomainEvent
import juju.sample.ColorAggregate.ChangeHeavy
import juju.sample.ColorPriorityAggregate.ColorAssigned
import juju.sample.PriorityActivitiesSaga.{EchoWakeUp, PriorityActivitiesActivate, PublishEcho}
import juju.sample.PriorityAggregate.PriorityIncreased
import juju.sample._
import juju.testkit.AkkaSpec

import scala.concurrent.duration._
import scala.reflect.ClassTag

trait SagaRouterSpec extends AkkaSpec {
  protected def createSagaRouter[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](tenant: String, probe: TestProbe) : ActorRef
  protected def publish(tenant: String, sagaRouterRef : ActorRef, event: DomainEvent, probe: TestProbe)
  protected def shutdownRouter[S <: Saga : ClassTag](tenant: String, sagaRouterRef : ActorRef, probe: TestProbe)

  it should "be able to start the saga due to events and receive an emitted command" in {
    val probe = TestProbe()
    probe.ignoreNoMsg()
    probe.ignoreMsg {
      case _ : SagaIsUp => true
    }
    val tenant = "T1"
    val routerRef = createSagaRouter[PriorityActivitiesSaga](tenant, probe)
    probe.send(routerRef, UpdateHandlers(Map.empty + (classOf[ChangeHeavy] -> probe.ref)))

    probe.expectMsgType[akka.actor.Status.Success](timeout.duration)

    publish(tenant, routerRef, ColorAssigned(1, "red"), probe)
    probe.expectMsgPF(timeout.duration) {
      case m@ChangeHeavy("red", _) =>
    }

    publish(tenant, routerRef, PriorityIncreased("x", 1), probe)
    probe.expectMsgPF(timeout.duration) {
      case m@ChangeHeavy("red", _) =>
    }

    shutdownRouter[PriorityActivitiesSaga](tenant, routerRef, probe)
  }

  it should "not route domain events depending specific conditions" in {
    val probe = TestProbe()

    probe.ignoreNoMsg()
    probe.ignoreMsg {
      case _: akka.actor.Status.Success => true
    }

    val tenant = "T2"
    val routerRef = createSagaRouter[PriorityActivitiesSaga](tenant, probe)
    probe.send(routerRef, UpdateHandlers(Map.empty + (classOf[ChangeHeavy] -> this.testActor)))

    publish(tenant, routerRef, PriorityIncreased("x", -1), probe) //-1 doesn't route event
    publish(tenant, routerRef, ColorAssigned(-1, "red"), probe)

    probe.expectNoMsg(3 seconds)
    shutdownRouter[PriorityActivitiesSaga](tenant, routerRef, probe)
  }

  it should "route wakeup event to all saga if registered" in {
    val probe = TestProbe()

    probe.ignoreMsg {case _: EchoWakeUp => true}

    val tenant = "T3"
    val routerRef = createSagaRouter[PriorityActivitiesSaga](tenant, probe)
    probe.send(routerRef, UpdateHandlers(Map.empty + (classOf[PublishEcho] -> probe.ref)))
    probe.expectMsgType[Success](timeout.duration)

    publish(tenant, routerRef, ColorAssigned(1, "red"), probe)
    probe.expectMsgType[SagaIsUp](timeout.duration)
    publish(tenant, routerRef, ColorAssigned(2, "yellow"), probe)
    probe.expectMsgType[SagaIsUp](timeout.duration)

    probe.send(routerRef, EchoWakeUp("hello world"))

    probe.expectMsgAllOf(timeout.duration,
      PublishEcho("echo from priority 1: hello world"),
      PublishEcho("echo from priority 2: hello world")
    )
    shutdownRouter[PriorityActivitiesSaga](tenant, routerRef, probe)
  }

  it should "activate saga by activate message" in {
    val probe = TestProbe()

    probe.ignoreNoMsg()
    probe.ignoreMsg {
      case _: PriorityActivitiesActivate => true
      case _: EchoWakeUp => true
      case _: akka.actor.Status.Success => true
    }

    val tenant = "T4"
    val routerRef = createSagaRouter[PriorityActivitiesSaga](tenant, probe)

    probe.send(routerRef, UpdateHandlers(Map.empty + (classOf[PublishEcho] -> probe.ref)))
    probe.send(routerRef, PriorityActivitiesActivate("1"))

    probe.expectMsgType[SagaIsUp](timeout.duration)

    probe.send(routerRef, EchoWakeUp("hello world"))

    probe.expectMsgAllOf(timeout.duration, PublishEcho("echo from priority 1: hello world"))
    shutdownRouter[PriorityActivitiesSaga](tenant, routerRef, probe)
  }

  it should "be able to route a events bound to all without correlation id" in {
    implicit def sagaFactory[S <: Saga : ClassTag]: SagaFactory[S] = ByConventions.sagaFactory[S]()
    implicit def sagaHandlersResolution[S <: Saga : ClassTag]: SagaHandlersResolution[S] = ByConventions.sagaHandlersResolution[S]()
    implicit def correlationIdResolution[S <: Saga : ClassTag]: SagaCorrelationIdResolution[S] = ByConventions.correlationIdResolution[S]()

    val probe = TestProbe()
    probe.ignoreNoMsg()
    probe.ignoreMsg {
      case _: AveragePersonWeightActivate => true
      case _: akka.actor.Status.Success => true
      case _: SagaIsUp => false
    }
    val tenant = "T5"
    val routerRef = createSagaRouter[AveragePersonWeightSaga](tenant, probe)
    probe.send(routerRef, UpdateHandlers(Map.empty + (classOf[PublishHello] -> probe.ref)))

    probe.send(routerRef, AveragePersonWeightActivate("fakeperson"))
    probe.expectMsgType[SagaIsUp](timeout.duration)

    publish(tenant, routerRef, HelloRequested("first message"), probe)
    probe.expectMsgPF(timeout.duration) {
      case m@PublishHello("fakeperson", "first message") =>
    }

    publish(tenant, routerRef, HelloRequested("second message"), probe)
    probe.expectMsgPF(timeout.duration) {
      case m@PublishHello("fakeperson", "second message") =>
    }

    shutdownRouter[AveragePersonWeightSaga](tenant, routerRef, probe)
  }
}

