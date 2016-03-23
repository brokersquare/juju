package juju.sample

import akka.actor.{ActorRef, Props}
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain._
import juju.messages.{Activate, Command, DomainEvent, WakeUp}
import juju.sample.ColorAggregate.ChangeHeavy
import juju.sample.ColorPriorityAggregate.ColorAssigned
import juju.sample.PriorityActivitiesSaga.{EchoReady, EchoWakeUp, PublishEcho}
import juju.sample.PriorityAggregate.{PriorityDecreased, PriorityIncreased}

object PriorityActivitiesSaga {
  case class PriorityActivitiesActivate(correlationId: String) extends Activate
  case class EchoWakeUp(message: String) extends WakeUp
  case class EchoReady(message: String) extends DomainEvent
  case class PublishEcho(message: String) extends Command

  implicit val correlationIdResolution = new SagaCorrelationIdResolution[PriorityActivitiesSaga] {
    override def resolve(event: DomainEvent): Correlate[String] = event match {
      case PriorityIncreased(_, p) if p == -1 => CorrelateNothing
      case PriorityIncreased(_, p) => CorrelateOne(p.toString)
      case PriorityDecreased(_, p) if p == -1  => CorrelateNothing
      case PriorityDecreased(_, p) => CorrelateOne(p.toString)
      case ColorAssigned(p, c) if p == -1  => CorrelateNothing
      case ColorAssigned(p, c) => CorrelateOne(p.toString)
    }
  }

  implicit val handlersResolution = new SagaHandlersResolution[PriorityActivitiesSaga] {
    override def resolve() = Seq(classOf[PriorityIncreased], classOf[PriorityDecreased], classOf[ColorAssigned])
    override def wakeUpBy() = Seq(classOf[EchoWakeUp])
    override def activateBy() = Some(classOf[PriorityActivitiesActivate])
  }

  implicit val factory = new SagaFactory[PriorityActivitiesSaga] {
    override def props(correlationId: String, commandRouter: ActorRef): Props = Props(classOf[PriorityActivitiesSaga], correlationId.toInt, commandRouter)
  }
}

class PriorityActivitiesSaga(val priority: Int, commandRouter: ActorRef) extends Saga {
  var color = ""
  var activities = 0

  override def applyEvent: PartialFunction[DomainEvent, Unit] = {
    case PriorityIncreased(_, p) => {
      activities = activities + 1
      deliveryChangeWeightIfNeeded(activities)
    }
    case PriorityDecreased(_, p) => {
      activities = activities + 1
      deliveryChangeWeightIfNeeded(activities)
    }
    case ColorAssigned(p, c) => {
      color = c
      deliveryChangeWeightIfNeeded(activities)
    }
    case EchoReady(message) =>
      deliverCommand(commandRouter, PublishEcho(s"echo from priority $priority: $message"))
  }

  override def receiveEvent: Receive = {
    case e@PriorityIncreased(_, p) if priority == p || priority == -1 => raise(e)
    case e@PriorityDecreased(_, p) if priority == p || priority == -1 => raise(e)
    case e@ColorAssigned(p, c) if priority == p || priority == -1 || c != color => raise(e)
    case EchoWakeUp(txt) => raise(EchoReady(txt))
  }

  def deliveryChangeWeightIfNeeded(activities : Int): Unit = {
    log.debug(s"delivery command to signal $activities")
    if (color != "") {
      deliverCommand(commandRouter, ChangeHeavy(color, activities))
    }
  }
}