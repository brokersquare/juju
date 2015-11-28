package juju.testkit.infrastructure

import akka.actor._
import juju.infrastructure._
import juju.sample.ColorAggregate.WeightChanged
import juju.sample.ColorPriorityAggregate.{AssignColor, ColorAssigned}
import juju.sample.PriorityAggregate._
import juju.sample.{ColorAggregate, ColorPriorityAggregate, PriorityActivitiesSaga, PriorityAggregate}
import juju.testkit.DomainSpec

import scala.language.existentials


abstract class EventBusSpec(prefix:String) extends DomainSpec(s"${prefix}EventBus") with Node {

  it should "be able to register handlers" in {
    withEventBus { busRef =>
      busRef ! RegisterHandlers[PriorityAggregate]

      expectMsgPF() {
        case HandlersRegistered(handlers) =>
          handlers should contain(classOf[CreatePriority])
          handlers should contain(classOf[IncreasePriority])
      }
    }
  }

  it should "not able to send a message with no registered handler" in {
    withEventBus { busRef =>
      busRef ! CreatePriority("fake")
      expectMsg(akka.actor.Status.Failure(HandlerNotDefinedException))
    }
  }

  it should "be able to send a command" in {
    withEventBus { busRef =>
      system.eventStream.subscribe(this.testActor, classOf[PriorityCreated])
      busRef ! RegisterHandlers[PriorityAggregate]
      expectMsgType[HandlersRegistered]
      busRef ! CreatePriority("fake")
      expectMsg(PriorityCreated("fake"))
    }
  }

  it should "be able to register saga" in {
    withEventBus { busRef =>
      busRef ! RegisterSaga[PriorityActivitiesSaga]()
      expectMsgPF() {
        case DomainEventsSubscribed(events) =>
          events should contain(classOf[PriorityIncreased])
          events should contain(classOf[PriorityDecreased])
          events should contain(classOf[ColorAssigned])
      }
    }
  }

  //TODO: test Activate messages

  it should "be able to execute saga workflow" in {
    withEventBus { busRef =>
      system.eventStream.subscribe(this.testActor, classOf[WeightChanged])
      busRef ! RegisterHandlers[PriorityAggregate]
      expectMsgType[HandlersRegistered]

      busRef ! RegisterHandlers[ColorAggregate]
      expectMsgType[HandlersRegistered]

      busRef ! RegisterHandlers[ColorPriorityAggregate]
      expectMsgType[HandlersRegistered]

      busRef ! RegisterSaga[PriorityActivitiesSaga]
      expectMsgType[DomainEventsSubscribed]

      busRef ! CreatePriority("x")
      busRef ! IncreasePriority("x")
      busRef ! AssignColor(1, "red")

      expectMsg(WeightChanged("red", 1))
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
