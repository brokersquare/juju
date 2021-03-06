package juju.domain

import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.util.Timeout
import juju.messages.{Activate, Command, DomainEvent, WakeUp}

import scala.annotation.tailrec
import scala.concurrent.Future

abstract class SagaFactory[S<: Saga] {
  def props(correlationId: String, bus: ActorRef) : Props
}

sealed abstract class Correlate[+A] extends Product with Serializable {
  def matchAll = false
  def matchOne = false
  def matchNone = false
  def get: A
  def isDefined: Boolean = !matchNone
}

final case class CorrelateOne[+A](x: A) extends Correlate[A] {
  override def matchOne = true
  def get = x
}

case object CorrelateAll extends Correlate[Nothing] {
  override def matchAll = true
  def get = throw new NoSuchElementException("CorrelateAll.get")
}

case object CorrelateNothing extends Correlate[Nothing] {
  override def matchNone = true
  def get = throw new NoSuchElementException("CorrelateNothing.get")
}

object Saga {
  trait SagaCorrelationIdResolution[S <: Saga] {
    /**
     * resolve returns an error if the event haven't to be handled (signal a wrong routing logic) otherwise returns CorrelateNothing if a handled event has specific condition, CorrelateAll if handled all saga instances, otherwise it returns the correlationid
     */
    def resolve(event: DomainEvent) : Correlate[String]
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

  private lazy val appliers = getMethods("apply", classOf[DomainEvent])
  private lazy val wakeups = getMethods("wakeup", classOf[WakeUp])

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
    implicit val ec = context.system.dispatcher
    implicit val timeout = Timeout(context.system.settings.config.getDuration("juju.eventbus.timeout", TimeUnit.SECONDS), TimeUnit.SECONDS)

    if (!recoveryRunning) {
      log.debug(s"delivery command '$command'")
      commandRouter.ask(command)(timeout)
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

  var stopped = false
  override def postStop() = {
    super.postStop()
    stopped = true
  }

  private def getMethods(methodname: String, parameterType: Class[_]) : Seq[Method] = {
    val clazz = this.getClass

    @tailrec def loop(c: Class[_], methods: Seq[Method]): Seq[Method] = {
      val m: Seq[Method] = c.getDeclaredMethods
        .filter(_.getParameterTypes.length == 1)
        .filter(_.getName == methodname)
        .filter(_.getParameterTypes.head != parameterType) ++ methods

      c.getSuperclass match {
        case s : Class[_] if s == classOf[Object] => m
        case s => loop(s, m)
      }
    }

    loop(clazz, List.empty)
  }
}