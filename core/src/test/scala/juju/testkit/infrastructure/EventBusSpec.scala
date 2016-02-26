package juju.testkit.infrastructure

import juju.domain.AggregateRoot.{AggregateHandlersResolution, AggregateIdResolution}
import juju.domain.resolvers.ByConventions
import juju.domain.{AggregateRoot, AggregateRootFactory}
import juju.infrastructure._
import juju.sample.ColorAggregate.WeightChanged
import juju.sample.ColorPriorityAggregate.{AssignColor, ColorAssigned}
import juju.sample.PersonAggregate._
import juju.sample.PriorityAggregate._
import juju.sample.{ColorAggregate, ColorPriorityAggregate, PriorityActivitiesSaga, PriorityAggregate, _}
import juju.testkit.AkkaSpec

import scala.language.existentials
import scala.reflect.ClassTag

trait EventBusSpec extends AkkaSpec {
  var _tenant = ""
  override def tenant = _tenant

  it should "be able to register handlers" in {
    _tenant = "t1"
    withEventBus { busRef =>
      busRef ! RegisterHandlers[PriorityAggregate]

      expectMsgPF(timeout.duration) {
        case HandlersRegistered(handlers) =>
          handlers should contain(classOf[CreatePriority])
          handlers should contain(classOf[IncreasePriority])
      }
    }
  }

  it should "not able to send a message with no registered handler" in {
    _tenant = "t2"
    withEventBus { busRef =>
      busRef ! CreatePriority("fake")
      expectMsg(akka.actor.Status.Failure(HandlerNotDefinedException))
    }
  }

  it should "be able to send a command" in {
    _tenant = "t3"
    ignoreNoMsg()
    ignoreMsg {
      case _ : HandlersRegistered => false
      case _ : PriorityCreated => false
      case _ => true
    }
    withEventBus(Seq(classOf[PriorityCreated])) { busRef =>
      busRef ! RegisterHandlers[PriorityAggregate]
      expectMsgType[HandlersRegistered](timeout.duration)
      busRef ! CreatePriority("fake")
      expectMsg(timeout.duration, PriorityCreated("fake"))
    }
  }

  it should "be able to delivery messages between aggregates" in {
    _tenant = "t4"
    implicit def idResolution[A <: AggregateRoot[_] : ClassTag]: AggregateIdResolution[A] = ByConventions.aggregateIdResolution[A]()
    implicit def factory[A <: AggregateRoot[_] : ClassTag]: AggregateRootFactory[A] = ByConventions.aggregateFactory[A]()
    implicit def handlersResolution[A <: AggregateRoot[_] : ClassTag]: AggregateHandlersResolution[A] = ByConventions.aggregateHandlersResolution[A]()

    ignoreMsg {
      case _ : HandlersRegistered => false
      case _ : PostcardDelivered => false
      case _ => true
    }

    withEventBus(Seq(classOf[PostcardDelivered])) { busRef =>
      busRef ! RegisterHandlers[PersonAggregate]
      expectMsgType[HandlersRegistered](timeout.duration)

      busRef ! CreatePerson("pippo")
      busRef ! CreatePerson("pluto")

      busRef ! SendPostcard("pluto", "pippo", "bau bau")
      expectMsg(timeout.duration, PostcardDelivered("pluto", "pippo", "bau bau"))
    }
  }

  it should "be able to register saga" in {
    _tenant = "t5"
    ignoreNoMsg()
    withEventBus { busRef =>
      busRef ! RegisterSaga[PriorityActivitiesSaga]()
      expectMsgPF(timeout.duration) {
        case DomainEventsSubscribed(events) =>
          events should contain(classOf[PriorityIncreased])
          events should contain(classOf[PriorityDecreased])
          events should contain(classOf[ColorAssigned])
      }
    }
  }

  //TODO: test Activate messages

  it should "be able to execute saga workflow" in {
    _tenant = "t6"
    withEventBus(Seq(classOf[WeightChanged])) { busRef =>
      ignoreMsg {
        case _ : HandlersRegistered => false
        case _ : DomainEventsSubscribed => false
        case _ : WeightChanged => false
        case _ => true
      }

      busRef ! RegisterHandlers[PriorityAggregate]
      expectMsgType[HandlersRegistered](timeout.duration)

      busRef ! RegisterHandlers[ColorAggregate]
      expectMsgType[HandlersRegistered](timeout.duration)

      busRef ! RegisterHandlers[ColorPriorityAggregate]
      expectMsgType[HandlersRegistered](timeout.duration)

      busRef ! RegisterSaga[PriorityActivitiesSaga]
      expectMsgType[DomainEventsSubscribed](timeout.duration)

      busRef ! CreatePriority("x")
      busRef ! IncreasePriority("x")
      busRef ! AssignColor(1, "red")

      expectMsgPF(timeout.duration) {
        case WeightChanged("red", _) =>
      }
    }
  }

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
