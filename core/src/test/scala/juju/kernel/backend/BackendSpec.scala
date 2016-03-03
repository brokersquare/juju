package juju.juju.kernel.backend


import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.resolvers.ByConventionsSpec.AggregateWithInvalidHandleAnnotation
import juju.domain.{SagaFactory, Saga, AggregateRoot, AggregateRootFactory}
import juju.infrastructure.local.LocalNode
import juju.infrastructure.{SagaRouterFactory, OfficeFactory, UpdateHandlers}
import juju.kernel.backend.{Backend, DefaultBackendConfig}
import juju.messages.{Boot, SystemIsUp}
import juju.sample.{AveragePersonWeightSaga, PersonAggregate}
import juju.testkit.LocalDomainSpec

import scala.reflect.ClassTag



abstract class BaseBackend(_appname: String) extends Backend with LocalNode with DefaultBackendConfig {
  override def appname: String = _appname
  override def role: String = "backend"
}

class BackendSpec extends LocalDomainSpec("BackendSpec") {

    it should "boot backend when no registration required" in {
      val probe = TestProbe()
      val backend = system.actorOf(Props(new BaseBackend("fakebackend"){
      }), "b1")
      probe.send(backend, Boot)
      probe.expectMsg(timeout.duration, SystemIsUp("fakebackend"))
    }


    it should "boot backend when all registration completed successful" in {
      class PersonAggregateExtended extends PersonAggregate  {}
      class AveragePersonWeightSagaExtended(correlationId: String, commandRouter: ActorRef) extends AveragePersonWeightSaga(correlationId, commandRouter)  {}

      val probe = TestProbe()
      val backend = system.actorOf(Props(new BaseBackend("fakebackend"){
        registerAggregate[PersonAggregate]()
        registerAggregate[PersonAggregateExtended]()
        registerSaga[AveragePersonWeightSaga]()
        registerSaga[AveragePersonWeightSagaExtended]()
      }), "b2")
      probe.send(backend, Boot)
      probe.expectMsg(timeout.duration, SystemIsUp("fakebackend"))
    }

    it should "fails if at least an aggregate registration fails" in {
      val probe = TestProbe()
      val backend = system.actorOf(Props(new BaseBackend("fakebackend") {

        override protected implicit def officeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](implicit system : ActorSystem): OfficeFactory[A] = {
          new OfficeFactory[A] {
            val aggregateName = implicitly[ClassTag[A]].runtimeClass.getSimpleName
            override val tenant: String = "b3"

            override def getOrCreate: ActorRef =system.actorOf(Props(new Actor {
              override def receive: Receive = {
                case UpdateHandlers(_) if aggregateName == "PersonAggregate" =>
                  sender ! akka.actor.Status.Success("xyz")
                case _ =>
                  sender ! akka.actor.Status.Failure(new Exception("not supported"))
              }
            }))
          }
        }

        registerAggregate[PersonAggregate]()
        registerAggregate[AggregateWithInvalidHandleAnnotation]()
        registerSaga[AveragePersonWeightSaga]()
      }), "b3")
      probe.send(backend, Boot)
      probe.expectMsgPF(timeout.duration) {
        case akka.actor.Status.Failure(_) =>
      }
    }

    it should "fails if at least a saga registration fails" in {
      class AveragePersonWeightSagaExtended(correlationId: String, commandRouter: ActorRef) extends AveragePersonWeightSaga(correlationId, commandRouter)  {}

      val probe = TestProbe()
      val backend = system.actorOf(Props(new BaseBackend("fakebackend") {

        override protected implicit def sagaRouterFactory[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](implicit system : ActorSystem): SagaRouterFactory[S] = new SagaRouterFactory[S] {
          override val tenant: String = "b4"
          val sagaName = implicitly[ClassTag[S]].runtimeClass.getSimpleName

          override def getOrCreate: ActorRef = system.actorOf(Props(new Actor {
            override def receive: Receive = {
              case UpdateHandlers(h) if sagaName == "AveragePersonWeightSaga" =>
                sender ! akka.actor.Status.Success(h)
              case _ =>
                sender ! akka.actor.Status.Failure(new Exception("not supported"))
            }
          }))
        }

        registerAggregate[PersonAggregate]()
        registerSaga[AveragePersonWeightSaga]()
        registerSaga[AveragePersonWeightSagaExtended]()
      }), "b4")
      probe.send(backend, Boot)
      probe.expectMsgPF(timeout.duration) {
        case akka.actor.Status.Failure(_) =>
      }
    }

    it should "fails if more aggregate and saga registration fails" in {
      class PersonAggregateExtended extends PersonAggregate {}
      class AveragePersonWeightSagaExtended(correlationId: String, commandRouter: ActorRef) extends AveragePersonWeightSaga(correlationId, commandRouter)  {}


      val probe = TestProbe()
      val backend = system.actorOf(Props(new BaseBackend("fakebackend") {

        override protected implicit def officeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](implicit system : ActorSystem): OfficeFactory[A] = {
          new OfficeFactory[A] {
            val aggregateName = implicitly[ClassTag[A]].runtimeClass.getSimpleName
            override val tenant: String = "b5"

            override def getOrCreate: ActorRef =system.actorOf(Props(new Actor {
              override def receive: Receive = {
                case _ =>
                  sender ! akka.actor.Status.Failure(new Exception("not supported"))
              }
            }))
          }
        }


        override protected implicit def sagaRouterFactory[S <: Saga : ClassTag : SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](implicit system : ActorSystem): SagaRouterFactory[S] = new SagaRouterFactory[S] {
          override val tenant: String = "b5"
          val sagaName = implicitly[ClassTag[S]].runtimeClass.getSimpleName

          override def getOrCreate: ActorRef = system.actorOf(Props(new Actor {
            override def receive: Receive = {
              case _ =>
                sender ! akka.actor.Status.Failure(new Exception("not supported"))
            }
          }))
        }

        registerAggregate[PersonAggregate]()
        registerAggregate[PersonAggregateExtended]()
        registerSaga[AveragePersonWeightSaga]()
        registerSaga[AveragePersonWeightSagaExtended]()
      }), "b5")
      probe.send(backend, Boot)
      probe.expectMsgPF(timeout.duration) {
        case akka.actor.Status.Failure(_) =>
      }
    }


  it should "retry registration after a failure" in {
    class PersonAggregateExtended extends PersonAggregate {}
    class AveragePersonWeightSagaExtended(correlationId: String, commandRouter: ActorRef) extends AveragePersonWeightSaga(correlationId, commandRouter)  {}

    val probe = TestProbe()
    val backend = system.actorOf(Props(new BaseBackend("fakebackend") {
      var fail = true

      override protected implicit def officeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](implicit system : ActorSystem): OfficeFactory[A] = {
        new OfficeFactory[A] {
          val aggregateName = implicitly[ClassTag[A]].runtimeClass.getSimpleName
          override val tenant: String = "b6"
          override def getOrCreate: ActorRef =system.actorOf(Props(new Actor {
            override def receive: Receive = {
              case UpdateHandlers(h) if fail  =>
                sender ! akka.actor.Status.Failure(new Exception("not supported"))
                fail = false
              case UpdateHandlers(h) =>
                sender ! akka.actor.Status.Success(h)
            }
          }))
        }
      }
      registerAggregate[PersonAggregate]()
      registerAggregate[PersonAggregateExtended]()
      registerSaga[AveragePersonWeightSaga]()
      registerSaga[AveragePersonWeightSagaExtended]()
    }), "b6")
    probe.send(backend, Boot)
    probe.expectMsg(timeout.duration, SystemIsUp("fakebackend"))
  }
}