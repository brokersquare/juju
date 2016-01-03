package juju.sample

import akka.actor.ActorRef
import juju.domain.Saga
import juju.messages.{Command, DomainEvent, WakeUp}
import juju.sample.AveragePersonWeightSaga.{PublishAverageWeight, PublishRequested, PublishWakeUp}
import juju.sample.PersonAggregate.WeightChanged

object AveragePersonWeightSaga {
  case class PublishWakeUp() extends WakeUp
  case class PublishAverageWeight(weight: Int) extends Command
  case class PublishRequested() extends DomainEvent
}

class AveragePersonWeightSaga(commandRouter: ActorRef) extends Saga {
  var weights : Map[String, Int] = Map.empty
  var average = 0
  var changed = false

  def apply(event: WeightChanged): Unit = {
    weights = weights.filterNot(_._1 == event.name) + (event.name -> event.weight)
    val newAverage = weights.values.sum / weights.toList.length
    changed = newAverage != average
    average = newAverage
  }

  def apply(event: PublishRequested): Unit =
    if (changed) {
      deliverCommand(commandRouter, PublishAverageWeight(average))
      changed = false
    }


  def wakeup(wakeup: PublishWakeUp): Unit = raise(PublishRequested())
}