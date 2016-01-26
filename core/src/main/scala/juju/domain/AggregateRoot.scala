package juju.domain

import akka.actor.{ActorRef, ActorLogging, Props}
import akka.persistence.PersistentActor
import juju.messages.{RouteTo, DomainEvent, Command}

import scala.reflect.ClassTag
import scala.language.existentials

class AggregateRootNotInitializedException extends Exception

abstract class AggregateRootFactory[A<: AggregateRoot[_]] {
  def props : Props
}

trait AggregateState {
  type StateMachine = PartialFunction[DomainEvent, AggregateState]
  def apply: StateMachine
}

case class EmptyState() extends AggregateState {
  override def apply = {
    case _ => EmptyState()
  }
}

object AggregateRoot {
  trait AggregateIdResolution[A <: AggregateRoot[_]] {
    def resolve(command: Command) : String
  }

  trait AggregateHandlersResolution[A <: AggregateRoot[_]] {
    def resolve() : Seq[Class[_ <: Command]]
  }
}

abstract class AggregateRoot[S <: AggregateState]
  extends PersistentActor with ActorLogging {

  def id = self.path.parent.name + '_' + self.path.name
  override def persistenceId: String = id
  log.debug(s"created AggregateRoot ${this.getClass.getCanonicalName} with id $id")
  private var stateOpt: Option[S] = None

  def isStateInitialized = stateOpt.isDefined
  protected def state = if (isStateInitialized) stateOpt.get else throw new AggregateRootNotInitializedException

  type AggregateStateFactory = PartialFunction[DomainEvent, S]
  val factory : AggregateStateFactory

  private lazy val handlers = this.getClass.getDeclaredMethods
    .filter(_.getParameterTypes.length == 1)
    .filter( _.getName == "handle")
    .filter(_.getParameterTypes.head != classOf[Command])

  def handle : Receive = {
    case cmd: Command if isCommandSupported(cmd) =>
      val handler = handlers.filter(_.getParameterTypes.head == cmd.getClass).head
      handler.invoke(this, cmd)
  }

  private def isCommandSupported(command: Command): Boolean =
    handlers.exists(_.getParameterTypes.head == command.getClass)

  def nextState(event: DomainEvent): S = {
    stateOpt match {
      case _ if stateOpt.isEmpty => factory.apply(event)
      case _ =>
        state.apply(event).asInstanceOf[S]
    }
  }

  override def receiveRecover: Receive = {
    case e: DomainEvent =>
      val next = nextState(e)
      stateOpt = Some(next)
    case _ =>
  }

  def raise(event: DomainEvent) = {
    persist(event) {
      case e: DomainEvent =>
        val next = nextState(event)
        stateOpt = Some(next)
        log.debug(s"event $e persisted and state has been changed")
        sender ! e
      case _ =>
    }
  }

  def raise(events: Seq[DomainEvent]) : Unit = {
    val s = sender()
    events match {
      //TODO: make persist and send back events fault tolerant
      case e +: rest =>
        val next = nextState(e)
        stateOpt = Some(next)
        log.debug(s"event $e persisted and state has been changed")
        sender ! e
        raise(rest)
      case e +: Nil =>
        raise(e)
      case Nil =>
    }
  }


  override def receiveCommand: Receive = {
    case cmd: Command =>
      handle(cmd)

    case m: RouteTo =>
      aggregateSenderClass = Some(m.senderClass.asInstanceOf[Class[AggregateRoot[_]]])
      aggregateSenderId = Some(m.senderId)

      handleAggregateMessage(m.message)

      aggregateSenderClass = None
      aggregateSenderId = None

    case _ =>
  }

  private var aggregateSenderClass: Option[Class[AggregateRoot[_]]] = None
  private var aggregateSenderId: Option[String] = None
  protected def aggregateSender() = (aggregateSenderClass.get, aggregateSenderId.get)

  protected def handleAggregateMessage : Receive = {
    case _ => ???
  }

  protected def deliveryMessageToAggregate[A <: AggregateRoot[_] : ClassTag](aggregateId: String, message: Any, router: ActorRef = sender()): Unit = {
    val senderClass = this.getClass.asInstanceOf[Class[_ <: AggregateRoot[_]]]
    val destinationClass = implicitly[ClassTag[A]].runtimeClass.asInstanceOf[Class[_ <: AggregateRoot[_]]]
    router ! RouteTo(senderClass, self.path.name, destinationClass, aggregateId, message)
  }

  var stopped = false
  override def postStop() = {
    super.postStop()
    stopped = true
  }
}