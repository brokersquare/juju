package juju.sample

import akka.actor.Props
import ColorAggregate.{ChangeWeight, WeightChanged}
import juju.domain.{AggregateRoot, AggregateState, AggregateRootFactory}
import AggregateRoot.{AggregateHandlersResolution, AggregateIdResolution}
import juju.domain.{AggregateState, AggregateRootFactory}
import juju.messages.{Command, DomainEvent}

object ColorAggregate {
  implicit val idResolution = new AggregateIdResolution[ColorAggregate] {
    def resolve(command: Command) : String = command match {
      case ChangeWeight(color, _) => color
      case _ => ???
    }
  }

  implicit val handlersResolution = new AggregateHandlersResolution[ColorAggregate] {
    def resolve() : Seq[Class[_ <: Command]] = {
      Seq(classOf[ChangeWeight])
    }
  }

  implicit val factory = new AggregateRootFactory[ColorAggregate] {
    override def props: Props = Props(classOf[ColorAggregate])
  }

  case class ChangeWeight(color: String, weight: Int) extends Command
  case class WeightChanged(color: String, weight: Int) extends DomainEvent
}

case class ColorState(color: String = "", weight: Int = 0) extends AggregateState {
  override def apply = {
    case WeightChanged(c, w) => copy(weight = w)
    case _ => ???
  }
}

class ColorAggregate extends AggregateRoot[ColorState] {
  override val factory: AggregateStateFactory = {
    case WeightChanged(color, v) => ColorState(color, v)
    case _ => throw new IllegalArgumentException("Cannot create state from event different that ColorAssigned type")
  }

  override def handle : Receive = {
    case ChangeWeight(c,w) => raise(WeightChanged(c,w))
  }
}