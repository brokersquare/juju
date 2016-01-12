package juju.sample

import akka.actor.Props
import ColorPriorityAggregate.{AssignColor, ColorAssigned}
import juju.domain.{AggregateRoot, AggregateState, AggregateRootFactory}
import AggregateRoot.{AggregateHandlersResolution, AggregateIdResolution}
import juju.domain.{AggregateState, AggregateRootFactory}
import juju.messages.{DomainEvent, Command}

object ColorPriorityAggregate {
  implicit val idResolution = new AggregateIdResolution[ColorPriorityAggregate] {
    def resolve(command: Command) : String = command match {
      case AssignColor(priority, _) => priority toString
      case _ => ???
    }
  }

  implicit val handlersResolution = new AggregateHandlersResolution[ColorPriorityAggregate] {
    def resolve() : Seq[Class[_ <: Command]] = {
      Seq(classOf[AssignColor])
    }
  }

  implicit val factory = new AggregateRootFactory[ColorPriorityAggregate] {
    override def props: Props = Props(classOf[ColorPriorityAggregate])
  }

  case class AssignColor(priority: Int, color: String) extends Command
  case class ColorAssigned(priority: Int, color: String) extends DomainEvent
}

case class ColorPriorityState(colorsOfPriorities : Map[Int, String] = Map.empty) extends AggregateState {
  override def apply = {
    case ColorAssigned(priority, color) =>
      val map = colorsOfPriorities.filter(_._1  == priority) + (priority -> color)
      copy(colorsOfPriorities = map)
    case _ => ???
  }
}

class ColorPriorityAggregate extends AggregateRoot[ColorPriorityState] {
  override val factory: AggregateStateFactory = {
    case c: ColorAssigned => ColorPriorityState().apply(c).asInstanceOf[ColorPriorityState]
    case _ => throw new IllegalArgumentException("Cannot create state from event different that ColorAssigned type")
  }

  override def handle : Receive = {
    case AssignColor(priority, color) => raise(ColorAssigned(priority, color))
  }
}