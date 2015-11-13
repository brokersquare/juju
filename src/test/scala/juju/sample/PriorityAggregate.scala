package juju.sample

import akka.actor.Props
import PriorityAggregate._
import juju.domain.{AggregateRoot, AggregateState, AggregateRootFactory}
import AggregateRoot.{AggregateHandlersResolution, AggregateIdResolution}
import juju.domain.{AggregateState, AggregateRootFactory}
import juju.messages.{DomainEvent, Command}

object PriorityAggregate {
  implicit val idResolution = new AggregateIdResolution[PriorityAggregate] {
    def resolve(command: Command) : String = command match {
      case CreatePriority(name) => name
      case IncreasePriority(name) => name
      case DecreasePriority(name) => name
      case _ => ???
    }
  }

  implicit val handlersResolution = new AggregateHandlersResolution[PriorityAggregate] {
    def resolve() : Seq[Class[_ <: Command]] = {
      Seq(classOf[CreatePriority], classOf[IncreasePriority], classOf[DecreasePriority])
    }
  }

  implicit val factory = new AggregateRootFactory[PriorityAggregate] {
    override def props: Props = Props(classOf[PriorityAggregate])
  }

  case class CreatePriority(name: String) extends Command
  case class PriorityCreated(name: String) extends DomainEvent

  case class IncreasePriority(name: String) extends Command
  case class PriorityIncreased(name: String, priority: Int) extends DomainEvent

  case class DecreasePriority(name: String) extends Command
  case class PriorityDecreased(name: String, priority: Int) extends DomainEvent
}

case class PriorityState(name: String = "", priority: Int = 0) extends AggregateState {
  override def apply = {
    case PriorityCreated(name) => copy(name = name, priority = 0)
    case PriorityIncreased(_, p) => copy(priority = p)
    case PriorityDecreased(_, p) => copy(priority = p)
    case _ => ???
  }
}


class PriorityAggregate extends AggregateRoot[PriorityState] {

  override val factory: AggregateStateFactory = {
    case PriorityCreated(name) => PriorityState(name)
    case _ => throw new IllegalArgumentException("Cannot create state from event different that Created type")
  }

  override def handle : Receive = {
    case CreatePriority(name) => raise(PriorityCreated(name))
    case IncreasePriority(name) => raise(PriorityIncreased(name, state.priority + 1))
    case DecreasePriority(name) => raise(PriorityDecreased(name, state.priority - 1))
  }
}