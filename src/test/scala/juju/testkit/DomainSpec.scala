package juju.testkit

import java.util.{Calendar, Date}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.gracefulStop
import akka.testkit._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers, TryValues}

import scala.concurrent.duration._

class DomainSpec(test: String,  config: Config = ConfigFactory.load("domain.conf"))
  extends { implicit val system = ActorSystem(test, config) }
  with TestKitBase
  with FlatSpecLike with Matchers with BeforeAndAfterAll with LazyLogging with TryValues
  with DefaultTimeout with ImplicitSender
{
  behavior of test      //this will print the behavior of the test

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
}