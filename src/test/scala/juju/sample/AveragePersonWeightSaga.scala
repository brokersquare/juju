package juju.sample
//import juju.sample.AveragePersonWeightSaga._
/*
object AveragePersonWeightSaga {
  case class ActivateAveragePersonWeight(override val correlationId: String) extends Activate
  case class PublishWakeUp() extends WakeUp
  case class PublishAverage(weight: Int) extends Command
  case class PublishRequested() extends DomainEvent
}

class AveragePersonWeightSaga(commandRouter: ActorRef) extends Saga {
  var weights : Map[String, Int] = Map.empty
  var average = 0
  var changed = false

  def apply(event: WeightChanged): Unit = {
    weights = weights.filterNot(_._1 == event.name) + (event.name -> event.weight)
    val newAverage = weights.values.sum / weights.toList.length
    changed = newAverage == average
    average = newAverage
  }

  def apply(event: PublishRequested): Unit = {
  }

  def wakeup(wakeup: PublishWakeUp): Unit = {
  }
}
*/

/*
object PriorityActivitiesSaga {
  case class PriorityActivitiesActivate(correlationId: String) extends Activate
  case class EchoWakeUp(message: String) extends WakeUp
  case class EchoReady(message: String) extends DomainEvent
  case class PublishEcho(message: String) extends Command

  implicit val correlationIdResolution = new SagaCorrelationIdResolution[PriorityActivitiesSaga] {
    override def resolve(event: DomainEvent): Option[String] = event match {
      case PriorityIncreased(_, p) if p == -1 => None
      case PriorityIncreased(_, p) => Some(p.toString)
      case PriorityDecreased(_, p) if p == -1  => None
      case PriorityDecreased(_, p) => Some(p.toString)
      case ColorAssigned(p, c) if p == -1  => None
      case ColorAssigned(p, c) => Some(p.toString)
      case _ => ???
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
      deliveryChangeWeightIfNeeded(0)
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
    case _ => ???
  }

  def deliveryChangeWeightIfNeeded(activities : Int): Unit = {
    log.debug(s"delivery command to signal $activities")
    if (color != "") {
      deliverCommand(commandRouter, ChangeWeight(color, activities))
    }
  }
}
*/