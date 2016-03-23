package juju.sample

import akka.actor.ActorRef
import juju.domain.Saga
import juju.domain.resolvers.{BindAll, ActivatedBy}
import juju.messages.{Activate, Command, DomainEvent, WakeUp}
import juju.sample.PersonAggregate.WeightChanged

case class AveragePersonWeightActivate(correlationId: String) extends Activate
case class PublishWakeUp() extends WakeUp
case class PublishAverageWeight(weight: Int) extends Command
case class PublishHello(correlationId: String, text: String) extends Command
@SerialVersionUID(1L) case class HelloRequested(text: String) extends DomainEvent
@SerialVersionUID(1L) case class PublishRequested() extends DomainEvent


@ActivatedBy(message = classOf[AveragePersonWeightActivate])
class AveragePersonWeightSaga(correlationId: String, commandRouter: ActorRef) extends Saga {
  var weights : Map[String, Int] = Map.empty
  var average = 0
  var changed = true

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

  @BindAll def apply(event: HelloRequested): Unit =
    deliverCommand(commandRouter, PublishHello(correlationId, event.text))

  def wakeup(wakeup: PublishWakeUp): Unit = raise(PublishRequested())
}