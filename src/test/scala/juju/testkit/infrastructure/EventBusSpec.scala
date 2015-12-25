package juju.testkit.infrastructure

import juju.infrastructure._
import juju.sample.ColorAggregate.WeightChanged
import juju.sample.ColorPriorityAggregate.{ColorAssigned, AssignColor}
import juju.sample.PriorityAggregate._
import juju.sample.{ColorAggregate, ColorPriorityAggregate, PriorityActivitiesSaga, PriorityAggregate}
import juju.testkit.AkkaSpec

import scala.language.existentials

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
      expectMsg(timeout.duration, akka.actor.Status.Failure(HandlerNotDefinedException()))
    }
  }

  it should "be able to send a command" in {
    _tenant = "t3"
    withEventBus(Seq(classOf[PriorityCreated])) { busRef =>
      busRef ! RegisterHandlers[PriorityAggregate]
      expectMsgType[HandlersRegistered](timeout.duration)
      busRef ! CreatePriority("fake")
      expectMsg(timeout.duration, PriorityCreated("fake"))
    }
  }

  it should "be able to register saga" in {
    _tenant = "t4"
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
    _tenant = "t5"
    withEventBus(Seq(classOf[WeightChanged])) { busRef =>

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
