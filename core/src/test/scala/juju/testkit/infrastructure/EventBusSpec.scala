package juju.testkit.infrastructure

import akka.testkit.TestProbe
import juju.domain.AggregateRoot.{AggregateHandlersResolution, AggregateIdResolution}
import juju.domain.resolvers.ByConventions
import juju.domain.{AggregateRoot, AggregateRootFactory}
import juju.infrastructure.{HandlersRegistered, RegisterHandlers, _}
import juju.sample.ColorAggregate.HeavyChanged
import juju.sample.ColorPriorityAggregate.{AssignColor, ColorAssigned}
import juju.sample.PersonAggregate.{CreatePerson, PostcardDelivered, SendPostcard}
import juju.sample.PriorityAggregate.{PriorityCreated, _}
import juju.sample.{PriorityAggregate, _}
import juju.testkit.AkkaSpec

import scala.language.existentials
import scala.reflect.ClassTag



trait EventBusSpec extends AkkaSpec {
  var _tenant = ""
  override def tenant = _tenant

  it should "be able to publish an event after a command send" in {
    _tenant = "t1"
    val probe = TestProbe()

    probe.ignoreMsg {
      case _ : HandlersRegistered => false
      case _ : PriorityCreated => false
      case _ => true
    }
    withEventBus(probe.ref, Seq(classOf[PriorityCreated])) { busRef =>
      probe.send(busRef, RegisterHandlers[PriorityAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)

      probe.send(busRef, CreatePriority("fake"))
      probe.expectMsg(timeout.duration, PriorityCreated("fake"))
    }
  }

  it should "be able to register handlers" in {
    _tenant = "t2"

    val probe = TestProbe()
    probe.ignoreMsg {
      case _ : HandlersRegistered => false
      case _ => true
    }

    withEventBus(probe.ref) { busRef =>
      probe.send(busRef, RegisterHandlers[PriorityAggregate])

      probe.expectMsgPF(timeout.duration) {
        case HandlersRegistered(handlers) =>
          handlers should contain(classOf[CreatePriority])
          handlers should contain(classOf[IncreasePriority])
      }
    }
  }

  it should "not able to send a message with no registered handler" in {
    _tenant = "t3"

    val probe = TestProbe()
    probe.ignoreMsg {
      case akka.actor.Status.Failure(HandlerNotDefinedException) => false
      case _ => true
    }

    withEventBus(probe.ref) { busRef =>
      probe.send(busRef, CreatePriority("fake"))
      probe.expectMsg(akka.actor.Status.Failure(HandlerNotDefinedException))
    }
  }

  it should "send an Ack when a successful command send" in {
    _tenant = "t4"

    val probe = TestProbe()
    probe.ignoreMsg {
      case _ : HandlersRegistered => false
      case akka.actor.Status.Success(_) => false
      case _ => true
    }
    withEventBus(probe.ref, Seq(classOf[PriorityCreated])) { busRef =>
      probe.send(busRef, RegisterHandlers[PriorityAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)

      probe.send(busRef, CreatePriority("fake"))
      probe.expectMsg(akka.actor.Status.Success(CreatePriority("fake")))
    }
  }

  it should "be able to delivery messages between aggregates" in {
    _tenant = "t5"

    val probe = TestProbe()
    probe.ignoreMsg {
      case _ : HandlersRegistered => false
      case _ : PostcardDelivered => false
      case _ => true
    }

    implicit def idResolution[A <: AggregateRoot[_] : ClassTag]: AggregateIdResolution[A] = ByConventions.aggregateIdResolution[A]()
    implicit def factory[A <: AggregateRoot[_] : ClassTag]: AggregateRootFactory[A] = ByConventions.aggregateFactory[A]()
    implicit def handlersResolution[A <: AggregateRoot[_] : ClassTag]: AggregateHandlersResolution[A] = ByConventions.aggregateHandlersResolution[A]()

    withEventBus(probe.ref, Seq(classOf[PostcardDelivered])) { busRef =>
      probe.send(busRef, RegisterHandlers[PersonAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)

      probe.send(busRef, CreatePerson("pippo"))
      probe.send(busRef, CreatePerson("pluto"))
      probe.send(busRef, SendPostcard("pluto", "pippo", "bau bau"))

      probe.expectMsg(timeout.duration, PostcardDelivered("pluto", "pippo", "bau bau"))
    }
  }

  it should "be able to register saga" in {
    _tenant = "t6"

    val probe = TestProbe()
    probe.ignoreMsg {
      case _ : DomainEventsSubscribed => false
      case _ => true
    }
    withEventBus(probe.ref) { busRef =>
      probe.send(busRef, RegisterSaga[PriorityActivitiesSaga]())
      probe.expectMsgPF(timeout.duration) {
        case DomainEventsSubscribed(events) =>
          events should contain(classOf[PriorityIncreased])
          events should contain(classOf[PriorityDecreased])
          events should contain(classOf[ColorAssigned])
      }
    }
  }

  //TODO: test Activate messages

  it should "be able to execute saga workflow" in {
    _tenant = "t7"

    val probe = TestProbe()
    probe.ignoreMsg {
      case _ : HandlersRegistered => false
      case _ : DomainEventsSubscribed => false
      case _ : HeavyChanged => false
      case _ => true
    }

    withEventBus(probe.ref, Seq(classOf[HeavyChanged])) { busRef =>

      probe.send(busRef, RegisterHandlers[PriorityAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)

      probe.send(busRef, RegisterHandlers[ColorAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)

      probe.send(busRef,  RegisterHandlers[ColorPriorityAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)

      probe.send(busRef, RegisterSaga[PriorityActivitiesSaga])
      probe.expectMsgType[DomainEventsSubscribed](timeout.duration)

      probe.send(busRef, CreatePriority("x"))
      probe.send(busRef, IncreasePriority("x"))
      probe.send(busRef, AssignColor(1, "red"))

      probe.expectMsgPF(timeout.duration) {
        case HeavyChanged("red", _) =>
      }
    }
  }

  it should "be idempotent when register handlers" in {
    _tenant = "t8"
    val probe = TestProbe()
    probe.ignoreMsg {
      case _ : HandlersRegistered => false
      case _ : PriorityCreated => false
      case _ => true
    }
    withEventBus(probe.ref, Seq(classOf[PriorityCreated])) { busRef =>
      probe.send(busRef, RegisterHandlers[PriorityAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)

      probe.send(busRef, RegisterHandlers[PriorityAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)
    }
  }

  it should "be idempotent when register saga" in {
    _tenant = "t9"
    val probe = TestProbe()
    probe.ignoreMsg {
      case _: DomainEventsSubscribed => false
      case _ => true
    }
    withEventBus(probe.ref) { busRef =>
      probe.send(busRef, RegisterSaga[PriorityActivitiesSaga]())
      probe.expectMsgPF(timeout.duration) {
        case DomainEventsSubscribed(events) =>
      }

      probe.send(busRef, RegisterSaga[PriorityActivitiesSaga]())
      probe.expectMsgPF(timeout.duration) {
        case DomainEventsSubscribed(events) =>
      }
    }
  }

//TODO: Add tests to check recovery of office and sagarouter after termination
    /*
    //TODO: tests not yet implemented
    it should "be able supervisor offices" in {
      assert(false, "not yet implemented")
    }

    it should "be able supervisor routers" in {
      assert(false, "not yet implemented")
    }
    */
}
