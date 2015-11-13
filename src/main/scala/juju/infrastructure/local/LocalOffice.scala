package juju.infrastructure.local

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.util.Timeout
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.{AggregateRoot, AggregateRootFactory}
import juju.infrastructure.{UpdateHandlers, OfficeFactory}
import juju.messages.{Command, DomainEvent}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object LocalOffice {
  implicit def localOfficeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](implicit system : ActorSystem) = {
    new OfficeFactory[A] {
      override def getOrCreate: ActorRef = {
        val actorName = s"$officeName"
        implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
        val retrieveChild = system.actorSelection(system.child(actorName)).resolveOne()

        Try(system.actorOf(Props(new LocalOffice[A]()), actorName)) match {
          case Success(ref) => ref
          case Failure(ex) => Await.ready(retrieveChild, 1 seconds).value.get.get
        }
      }
    }
  }
}

class LocalOffice[A <: AggregateRoot[_]](implicit ct: ClassTag[A], idResolver : AggregateIdResolution[A], aggregateFactory : AggregateRootFactory[A])
  extends Actor with ActorLogging {
  val aggregateName = implicitly[ClassTag[A]].runtimeClass.getSimpleName //TODO: take it from the officefactory

  override def receive: Receive = {
    case cmd: Command =>
      val aggregateId = idResolver.resolve(cmd)
      log.debug(s"received command '$cmd' with id '$aggregateId'")
      val aggregateRef = context.child(aggregateId).getOrElse({
        val ref = context.actorOf(aggregateFactory.props, aggregateId)
        log.debug(s"office $self create aggregate $ref")
        ref
      })
      aggregateRef ! cmd
    case event : DomainEvent =>
      log.debug(s"received back event $event")
      context.system.eventStream.publish(event)
    case UpdateHandlers(_) =>
      //log.debug(s"received update handlers => ignore (office cannot route command. Useful only for the saga router)")
      sender ! akka.actor.Status.Success(s"$aggregateName")
    case m =>
      log.debug(s"discard message $m")
  }
}