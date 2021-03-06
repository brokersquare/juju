package juju.testkit
import java.util.{Calendar, Date}

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.gracefulStop
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKitBase, TestProbe}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import juju.infrastructure.{EventBus, Node}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers, TryValues}

import scala.concurrent.duration._

trait AkkaSpec extends TestKitBase
  with FlatSpecLike with Matchers with BeforeAndAfterAll with LazyLogging with TryValues
  with DefaultTimeout with ImplicitSender with Node {
  val config : Config
  implicit val system : ActorSystem

  val test: String

  override def beforeAll() = {
    System.setProperty("java.net.preferIPv4Stack", "true") //TODO: move property declaration to the build.sbt
  }

  override def afterAll() = {
    shutdown()
  }

  def date(daysFromStart: Int, start: Date = new Date(0)): Date = {
    val cal = Calendar.getInstance()
    cal.setTime(new Date(0))
    cal.add(Calendar.DATE, daysFromStart)
    cal.getTime
  }

  protected def withProbe(actorRef : ActorRef = null)(action: TestProbe => Unit): Unit = {
    val probe = new TestProbe(system)
    action(probe)
    if (actorRef != null) {
      gracefulStop(actorRef, 5 seconds)
    }
    gracefulStop(probe.ref, 5 seconds)
    gracefulStop(this.testActor, 5 seconds)
  }

  protected def withActorUnderTest(props: Props)(action: ActorRef => Unit): Unit = {
    withActorUnderTest(()=>system.actorOf(props, "ActorUnderTest"))(action)
  }

  protected def withActorUnderTest(factory: ()=> ActorRef)(action: ActorRef => Unit): Unit = {
    val sutRef = factory()
    action(sutRef)
    gracefulStop(sutRef, 5 seconds)
    gracefulStop(this.testActor, 5 seconds)
  }


  protected def withEventBus(subscriber: ActorRef)(action : ActorRef => Unit): Unit = {
    withEventBus(subscriber, Seq.empty)(action)
  }

  protected def withEventBus(subscriber: ActorRef, subscribedEvents : Seq[Class[_]])(action : ActorRef => Unit) = {
    system.eventStream.unsubscribe(subscriber)
    var router : ActorRef = null
    var bus: ActorRef = null

    subscribedEvents.foreach { ec =>
      system.eventStream.subscribe(subscriber, ec)
    }

    try {
      router = system.actorOf(DeadLetterRouter.props(subscriber), EventBus.nameWithTenant(tenant, "DeadLetterRouter"))
      bus = system.actorOf(EventBus.props(tenant), EventBus.actorName(tenant))
      action(bus)
    } finally {
      subscribedEvents.foreach { ec =>
        system.eventStream.subscribe(subscriber, ec)
      }

      system.eventStream.unsubscribe(subscriber)

      if (router != null) router ! PoisonPill
      if (bus != null) bus ! juju.messages.ShutdownActor
    }
  }
}
