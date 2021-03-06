package juju.testkit.infrastructure

import akka.actor.Props
import akka.testkit.TestProbe
import juju.domain.AggregateRoot.{AggregateHandlersResolution, AggregateIdResolution}
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain._
import juju.domain.resolvers.ByConventions
import juju.infrastructure.{HandlersRegistered, RegisterHandlers, _}
import juju.messages.Command
import juju.sample.ColorAggregate.HeavyChanged
import juju.sample.ColorPriorityAggregate.{AssignColor, ColorAssigned}
import juju.sample.PersonAggregate.{WeightChanged, CreatePerson, PostcardDelivered, SendPostcard}
import juju.sample.PriorityAggregate.{PriorityCreated, _}
import juju.sample.{AveragePersonWeightActivate, PriorityAggregate, _}
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
    withEventBus(probe.ref, Seq(classOf[PriorityCreated])) { bus =>
      probe.send(bus, RegisterHandlers[PriorityAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)

      probe.send(bus, CreatePriority("fake"))
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

    withEventBus(probe.ref) { bus =>
      probe.send(bus, RegisterHandlers[PriorityAggregate])

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

    withEventBus(probe.ref) { bus =>
      probe.send(bus, CreatePriority("fake"))
      probe.expectMsg(akka.actor.Status.Failure(HandlerNotDefinedException))
    }
  }

  it should "send an Ack when a successful command send" in {
    _tenant = "t4"
    if (test == "KafkaEventBus") pending //this test make some other tests failure in case of Kafka

    val probe = TestProbe()
    probe.ignoreNoMsg()
    probe.ignoreMsg {
      case _ : HandlersRegistered => false
      case akka.actor.Status.Success(_) => false
      case _ => true
    }
    withEventBus(probe.ref, Seq(classOf[PriorityCreated])) { bus =>
      probe.send(bus, RegisterHandlers[PriorityAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)

      probe.send(bus, CreatePriority("fake"))
      probe.expectMsg(timeout.duration, akka.actor.Status.Success(CreatePriority("fake")))
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

    withEventBus(probe.ref, Seq(classOf[PostcardDelivered])) { bus =>
      probe.send(bus, RegisterHandlers[PersonAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)

      probe.send(bus, CreatePerson("pippo"))
      probe.send(bus, CreatePerson("pluto"))
      probe.send(bus, SendPostcard("pluto", "pippo", "bau bau"))

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
    withEventBus(probe.ref) { bus =>
      probe.send(bus, RegisterSaga[PriorityActivitiesSaga]())
      probe.expectMsgPF(timeout.duration) {
        case DomainEventsSubscribed(events) =>
          events should contain(classOf[PriorityIncreased])
          events should contain(classOf[PriorityDecreased])
          events should contain(classOf[ColorAssigned])
      }
    }
  }

  it should "be able to execute saga workflow" in {
    _tenant = "t7"

    val probe = TestProbe()
    probe.ignoreMsg {
      case _ : HandlersRegistered => false
      case _ : DomainEventsSubscribed => false
      case _ : HeavyChanged => false
      case _ => true
    }

    withEventBus(probe.ref, Seq(classOf[HeavyChanged])) { bus =>

      probe.send(bus, RegisterHandlers[PriorityAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)

      probe.send(bus, RegisterHandlers[ColorAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)

      probe.send(bus,  RegisterHandlers[ColorPriorityAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)

      probe.send(bus, RegisterSaga[PriorityActivitiesSaga])
      probe.expectMsgType[DomainEventsSubscribed](timeout.duration)

      probe.send(bus, CreatePriority("x"))
      probe.send(bus, IncreasePriority("x"))
      probe.send(bus, AssignColor(1, "red"))

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
    withEventBus(probe.ref, Seq(classOf[PriorityCreated])) { bus =>
      probe.send(bus, RegisterHandlers[PriorityAggregate])
      probe.expectMsgType[HandlersRegistered](timeout.duration)

      probe.send(bus, RegisterHandlers[PriorityAggregate])
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
    withEventBus(probe.ref) { bus =>
      probe.send(bus, RegisterSaga[PriorityActivitiesSaga]())
      probe.expectMsgPF(timeout.duration) {
        case DomainEventsSubscribed(events) =>
      }

      probe.send(bus, RegisterSaga[PriorityActivitiesSaga]())
      probe.expectMsgPF(timeout.duration) {
        case DomainEventsSubscribed(events) =>
      }
    }
  }

  it should "receive an Ack after activate a saga" in {
    _tenant = "t10"

    implicit def sagaFactory[S <: Saga : ClassTag]: SagaFactory[S] = ByConventions.sagaFactory[S]()
    implicit def sagaHandlersResolution[S <: Saga : ClassTag]: SagaHandlersResolution[S] = ByConventions.sagaHandlersResolution[S]()
    implicit def correlationIdResolution[S <: Saga : ClassTag]: SagaCorrelationIdResolution[S] = ByConventions.correlationIdResolution[S]()

    val probe = TestProbe()
    probe.ignoreNoMsg()
    withEventBus(probe.ref) { bus =>
      probe.send(bus, RegisterSaga[AveragePersonWeightSaga]())
      probe.expectMsgPF(timeout.duration) {
        case DomainEventsSubscribed(events) =>
      }
      probe.send(bus, AveragePersonWeightActivate("fake"))
      probe.expectMsg(timeout.duration, akka.actor.Status.Success(AveragePersonWeightActivate("fake")))
    }
  }

  it should "receive an Ack after wakeup a saga" in {
    _tenant = "t11"
    implicit def sagaFactory[S <: Saga : ClassTag]: SagaFactory[S] = ByConventions.sagaFactory[S]()
    implicit def sagaHandlersResolution[S <: Saga : ClassTag]: SagaHandlersResolution[S] = ByConventions.sagaHandlersResolution[S]()
    implicit def correlationIdResolution[S <: Saga : ClassTag]: SagaCorrelationIdResolution[S] = ByConventions.correlationIdResolution[S]()

    val probe = TestProbe()
    probe.ignoreNoMsg()
    withEventBus(probe.ref) { bus =>
      probe.send(bus, RegisterSaga[AveragePersonWeightSaga]())
      probe.expectMsgPF(timeout.duration) {
        case DomainEventsSubscribed(events) =>
      }
      probe.send(bus, PublishWakeUp())
      probe.expectMsg(timeout.duration, akka.actor.Status.Success(PublishWakeUp()))
    }
  }

  it should "be able to route a wakeup message to a saga" in {
    _tenant = "t12"

    class PublisherAggregate extends AggregateRoot[EmptyState] {
      override val factory: AggregateStateFactory = {
        case _ => EmptyState()
      }

      def handle(command: PublishAverageWeight): Unit = command match {
        case _ => raise(WeightChanged("xxx", command.weight))
      }
    }

    implicit def idResolution[A] = new AggregateIdResolution[PublisherAggregate] {
      override def resolve(command: Command): String =  "fake"
    }

    implicit def factory[A] = new AggregateRootFactory[PublisherAggregate] {
      override def props: Props = Props(new PublisherAggregate())
    }

    implicit def handlersResolution[A] = new AggregateHandlersResolution[PublisherAggregate] {
      override def resolve(): Seq[Class[_ <: Command]] = Seq(classOf[PublishAverageWeight])
    }

    implicit def sagaFactory[S <: Saga : ClassTag]: SagaFactory[S] = ByConventions.sagaFactory[S]()
    implicit def sagaHandlersResolution[S <: Saga : ClassTag]: SagaHandlersResolution[S] = ByConventions.sagaHandlersResolution[S]()
    implicit def correlationIdResolution[S <: Saga : ClassTag]: SagaCorrelationIdResolution[S] = ByConventions.correlationIdResolution[S]()

    val probe = TestProbe()
    probe.ignoreNoMsg()

    withEventBus(probe.ref, Seq(classOf[WeightChanged])) { bus =>
      probe.send(bus, RegisterHandlers[PublisherAggregate]())
      probe.expectMsgPF(timeout.duration) {
        case HandlersRegistered(_) =>
      }
      probe.send(bus, RegisterSaga[AveragePersonWeightSaga]())
      probe.expectMsgPF(timeout.duration) {
        case DomainEventsSubscribed(events) =>
      }

      probe.send(bus, AveragePersonWeightActivate("xxx"))
      probe.expectMsg(timeout.duration, akka.actor.Status.Success(AveragePersonWeightActivate("xxx")))

      probe.ignoreMsg {
        case _: WeightChanged => false
        case _ => true
      }

      probe.send(bus, PublishWakeUp())
      probe.expectMsg(timeout.duration, WeightChanged("xxx", 0))
    }
  }

  //TODO: Add tests to check recovery of office and sagarouter after termination

  it should "be able supervisor offices" in {
    pending
    assert(false, "not yet implemented")
  }

  it should "be able supervisor routers" in {
    pending
    assert(false, "not yet implemented")
  }

}
