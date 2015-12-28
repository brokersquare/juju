package juju.sample

import juju.domain.{AggregateRoot, AggregateState, Handle}
import juju.messages.{Command, DomainEvent}
import juju.sample.PersonAggregate.{ChangeWeight, WeightChanged}

object PersonAggregate {
  case class ChangeWeight(name: String, weight: Int) extends Command
  case class WeightChanged(name: String, weight: Int) extends DomainEvent
}

case class PersonState(name: String = "", weight: Int = 0) extends AggregateState {
  override def apply = {
    case WeightChanged(c, w) => copy(weight = w)
  }
}

class PersonAggregate extends AggregateRoot[PersonState] with Handle[ChangeWeight] {
  override val factory: AggregateStateFactory = {
    case WeightChanged(name, v) => PersonState(name, v)
    case _ => throw new IllegalArgumentException("Cannot create state from event different that WeightChanged type")
  }

  override def handle(command: ChangeWeight): Unit = command match {
    case ChangeWeight(c,w) => raise(WeightChanged(c,w))
  }
}
