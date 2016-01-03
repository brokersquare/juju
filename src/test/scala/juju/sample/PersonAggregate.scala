package juju.sample

import juju.domain.resolvers.AggregateIdField
import juju.domain.{AggregateRoot, AggregateState}
import juju.messages.{Command, DomainEvent}
import juju.sample.PersonAggregate._

object PersonAggregate {
  case class CreatePerson(name: String) extends Command
  case class ChangeHeight(name: String, height: Int) extends Command
  case class ChangeWeight(name: String, weight: Int) extends Command

  case class PersonCreated(name: String) extends DomainEvent
  case class WeightChanged(name: String, weight: Int) extends DomainEvent
  case class HeightChanged(name: String, height: Int) extends DomainEvent
}

case class PersonState(name: String = "", weight: Int = 0, height: Int = 0) extends AggregateState {
  override def apply = {
    case WeightChanged(c, w) => copy(weight = w)
    case HeightChanged(c, h) => copy(height = h)
  }
}

class PersonAggregate extends AggregateRoot[PersonState] {
  override val factory: AggregateStateFactory = {
    case PersonCreated(name) => PersonState(name)
    case _ => throw new IllegalArgumentException("Cannot create state from event different than CreatePerson type")
  }

  @AggregateIdField(fieldname = "name") def handle(command: CreatePerson): Unit = raise(PersonCreated(command.name))

  @AggregateIdField(fieldname = "name") def handle(command: ChangeWeight): Unit = raise(WeightChanged(command.name, command.weight))

  @AggregateIdField(fieldname = "name") def handle(command: ChangeHeight): Unit = raise(HeightChanged(command.name, command.height))
}
