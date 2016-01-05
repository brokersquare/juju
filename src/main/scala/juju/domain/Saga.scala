package juju.domain

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.persistence.{PersistentActor, RecoveryCompleted}
import juju.messages.{Command, DomainEvent, Activate, WakeUp}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

abstract class SagaFactory[S<: Saga] {
  def props(correlationId: String, bus: ActorRef) : Props
}

object Saga {
  trait SagaCorrelationIdResolution[S <: Saga] {
    /**
     * resolve returns an error if the event haven't to be handled (signal a wrong routing logic) otherwise returns None if a handled event has specific condition, otherwise it returns the correlationid
     */
    def resolve(event: DomainEvent) : Option[String]
  }

  trait SagaHandlersResolution[S <: Saga] {
    def resolve() : Seq[Class[_ <: DomainEvent]]
    def activateBy() : Option[Class[_ <: Activate]] = None
    def wakeUpBy() : Seq[Class[_ <: WakeUp]]
  }
}

trait Saga extends PersistentActor with ActorLogging {
  private var completed = false
  protected def isCompleted = completed
  protected def markAsCompleted() = completed = true

  def sagaId = self.path.parent.name + '_' + self.path.name
  log.debug(s"created Saga ${this.getClass.getCanonicalName} with id $sagaId")
  override def persistenceId: String = sagaId

  private lazy val appliers = this.getClass.getDeclaredMethods
    .filter(_.getParameterTypes.length == 1)
    .filter( _.getName == "apply")
    .filter(_.getParameterTypes.head != classOf[DomainEvent])

  private lazy val wakeups = this.getClass.getDeclaredMethods
    .filter(_.getParameterTypes.length == 1)
    .filter( _.getName == "wakeup")
    .filter(_.getParameterTypes.head != classOf[WakeUp])

  /**
   * Event handler called on state transition
   */
  def applyEvent: PartialFunction[DomainEvent, Unit] = {
    case event: DomainEvent if isDomainEventSupported(event) =>
      val applier = appliers.filter(_.getParameterTypes.head == event.getClass).head
      applier.invoke(this, event)
  }

  private def isDomainEventSupported(event: DomainEvent): Boolean =
    appliers.exists(_.getParameterTypes.head == event.getClass)

  private def isWakeupSupported(wakeup: WakeUp): Boolean =
    wakeups.exists(_.getParameterTypes.head == wakeup.getClass)

  /**
   * Defines business process logic (state transitions).
   * State transition happens when raise(event) is called.
   * No state transition indicates the current event message could have been received out-of-order.
   */
  def receiveEvent: Receive = {
    case e: DomainEvent if !completed => raise(e)
    case e: DomainEvent  =>
    case w: WakeUp if isWakeupSupported(w) && !completed =>
      val wakeup = wakeups.filter(_.getParameterTypes.head == w.getClass).head
      wakeup.invoke(this, w)
    case w: WakeUp if isWakeupSupported(w) =>
  }


  protected def deliverCommand(commandRouter: ActorRef, command: Command): Future[Any] = {
    if (!recoveryRunning) {
      log.debug(s"delivery command '$command'")
      commandRouter.ask(command)(60 seconds) //TODO: make timeout as parameter (implicit?)
    } else {
      Future(None)
    }
  }

  def raise(e: DomainEvent): Unit =
    persist(e) { persisted => //TODO: saga events should be marked so they are considered only during the aggregate state replay not for read mode event processing (otherwise they can be processed twice, one for the aggregate source and one for the saga!)
      log.debug("Domain Event persisted: {}", persisted)
      applyEvent(e)
    }

  override def receiveRecover: Receive = {
    case rc: RecoveryCompleted =>
    // do nothing
    case e: DomainEvent =>
      applyEvent(e)
  }

  override def receiveCommand: Receive = {
    case e : DomainEvent => receiveEvent(e)
    case e : WakeUp => receiveEvent(e)
    case m@_ =>
      log.warning(s"unexpected message...: '$m'")
  }
}