package juju.domain

import java.lang.reflect.Method

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.PersistentActor
import juju.domain.AggregateRoot.{CommandReceiveFailure, CommandReceived, CommandNotSupported}
import juju.messages.{Command, DomainEvent, RouteTo}

import scala.annotation.tailrec
import scala.language.existentials
import scala.reflect.ClassTag
import scala.util.Try

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
  case class CommandReceived(command: Command)
  case class CommandReceiveFailure(command: Command, cause: Throwable) extends Exception(cause)
  case class CommandNotSupported(command: Command) extends Exception

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

  private lazy val handlers = getMethods("handle", classOf[Command])

  def handle : Receive = {
    case cmd: Command if isCommandSupported(cmd) =>
      val handler = handlers.filter(_.getParameterTypes.head == cmd.getClass).head
      handler.invoke(this, cmd)
    case cmd: Command if !isCommandSupported(cmd) =>throw CommandNotSupported(cmd)
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
      val s = sender()
      Try {
        handle(cmd)
      } match {
        case scala.util.Success(r) =>
          s ! akka.actor.Status.Success(CommandReceived(cmd))
        case scala.util.Failure(e) =>
          s ! akka.actor.Status.Failure(CommandReceiveFailure(cmd, e))
      }

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

  protected def handleAggregateMessage : Receive = {case _ => ???}


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