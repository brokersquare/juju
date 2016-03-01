package juju.sample

import akka.actor.Props
import juju.domain.AggregateRoot.{AggregateHandlersResolution, AggregateIdResolution}
import juju.domain.{AggregateRoot, AggregateRootFactory, AggregateState}
import juju.messages.{Command, DomainEvent}
import juju.sample.ColorAggregate.{ChangeHeavy, HeavyChanged}

object ColorAggregate {
  implicit val idResolution = new AggregateIdResolution[ColorAggregate] {
    def resolve(command: Command) : String = command match {
      case ChangeHeavy(color, _) => color
      case _ => ???
    }
  }

  implicit val handlersResolution = new AggregateHandlersResolution[ColorAggregate] {
    def resolve() : Seq[Class[_ <: Command]] = {
      Seq(classOf[ChangeHeavy])
    }
  }

  implicit val factory = new AggregateRootFactory[ColorAggregate] {
    override def props: Props = Props(classOf[ColorAggregate])
  }

  case class ChangeHeavy(color: String, heavy: Int) extends Command
  case class HeavyChanged(color: String, heavy: Int) extends DomainEvent
}

case class ColorState(color: String = "", heavy: Int = 0) extends AggregateState {
  override def apply = {
    case HeavyChanged(c, h) => copy(heavy = h)
    case _ => ???
  }
}

class ColorAggregate extends AggregateRoot[ColorState] {
  override val factory: AggregateStateFactory = {
    case HeavyChanged(color, v) => ColorState(color, v)
    case _ => throw new IllegalArgumentException("Cannot create state from event different that ColorAssigned type")
  }

  override def handle : Receive = {
    case ChangeHeavy(c,h) => raise(HeavyChanged(c,h))
  }
}