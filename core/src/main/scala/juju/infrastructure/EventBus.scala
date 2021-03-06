package juju.infrastructure

import java.util.concurrent.TimeUnit

import akka.actor.Status.Failure
import akka.actor._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import juju.domain.AggregateRoot.AggregateHandlersResolution
import juju.domain.Saga.SagaHandlersResolution
import juju.domain._
import juju.messages._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.existentials

object EventBus {
  def props(tenant: String = "") = Props(classOf[EventBus], tenant)
  def actorName(tenant: String = "") = nameWithTenant(tenant, "Bus")
  def proxyActorName(tenant: String = "") = nameWithTenant(tenant, "BusProxy")

  def nameWithTenant(tenant: String, name: String): String = {
    tenant match {
      case t if t == null || t.trim == "" => name
      case _ => s"${tenant}_$name"
    }
  }

  def nameWithTenant(tenant: String, message: Class[_]): String = nameWithTenant(tenant, message.getSimpleName)

  def nameWithTenant(tenant: String, message: Message): String = nameWithTenant(tenant, message.getClass)
}

case object HandlerNotDefinedException extends Exception
case class CommandSendFailure(command: Command, cause: Throwable) extends Exception(cause)
case class ActivationSendFailure(activate: Activate, cause: Throwable) extends Exception(cause)
//case class WakeUpSendFailure(wakeUp: WakeUp, cause: Throwable) extends Exception(cause)

case class RegisterHandlersFailure(register: RegisterHandlers[_ <: AggregateRoot[_]], cause: Throwable) extends Exception(cause)
case class RegisterSagaFailure(register: RegisterSaga[_ <: Saga], cause: Throwable) extends Exception(cause)
case class MessageNotSupported(text: String) extends Exception(text)

case class UpdateHandlers(handlers : Map[Class[_ <: Command], ActorRef]) extends InfrastructureMessage

case class RegisterHandlers[A <: AggregateRoot[_]](implicit val officeFactory: OfficeFactory[A], val resolver: AggregateHandlersResolution[A]) extends InfrastructureMessage
case class HandlersRegistered(handlers : Iterable[Class[_ <: Command]]) extends InfrastructureMessage
case class RegisterSaga[S <: Saga](implicit val routerFactory : SagaRouterFactory[S], val resolver: SagaHandlersResolution[S]) extends InfrastructureMessage
case object GetSubscribedDomainEvents extends InfrastructureMessage
case class DomainEventsSubscribed(events: Iterable[Class[_ <: DomainEvent]]) extends InfrastructureMessage

class EventBus(tenant: String) extends Actor with ActorLogging with Stash {
  var registerMessages: Map[ActorRef, InfrastructureMessage] = Map.empty
  var booting: Seq[ActorRef] = Seq.empty

  var handlers = Map[Class[_ <: Command], ActorRef]()
  var routers = Map[Class[_ <: DomainEvent], ActorRef]()
  var activates = Map[Class[_ <: Activate], ActorRef]()
  var wakeUps = Map[Class[_ <: WakeUp], Seq[ActorRef]]()
  implicit val timeout = Timeout(context.system.settings.config.getDuration("juju.eventbus.timeout", TimeUnit.SECONDS), TimeUnit.SECONDS)
  implicit val executeContext = context.dispatcher

  log.debug("EventBus is up and running")

  override def receive: Receive = {
    case command : Command =>
      log.debug(s"eventbus command $command received")

      val commandType = command.getClass

      handlers.get(commandType) match {
        case Some(office) =>
          val s = sender()
          office.ask(command)(timeout.duration, self)
            .map { case _ =>
              log.debug(s"command $command sent to $s")
              s ! akka.actor.Status.Success(command)
            }.onFailure { case f =>
            log.warning(s"cannot send $command to $s due to $f")
            s ! akka.actor.Status.Failure(CommandSendFailure(command, f))
          }
        case None =>
          sender ! akka.actor.Status.Failure(HandlerNotDefinedException)
      }

    case activate : Activate =>
      log.debug(s"activator $activate requested")
      activates.get(activate.getClass) match {
        case Some(destRef) =>
          val s = sender()
          destRef.ask(activate)(timeout.duration, self)
            .map {case _ =>
              s ! akka.actor.Status.Success(activate)
              log.debug(s"activate $activate sent to $destRef")
            }
            .onFailure {case f =>
              s ! akka.actor.Status.Failure(ActivationSendFailure(activate,f))
              log.warning(s"cannot send $activate to $destRef due to $f")
            }

        case None =>
      }

    case wakeUp : WakeUp =>
      val s = sender()
      log.debug(s"sending wakeup $wakeUp requested")
      val wakeUpClass = wakeUp.getClass
      wakeUps.get(wakeUpClass) match {
        case Some(dests) =>
          val futures = dests map { d =>
            log.debug(s"sending wake up event $wakeUp to $d")
            d.ask(wakeUp)(timeout.duration, s)
          }

          Future.sequence(futures) onComplete {
            case scala.util.Success(results) =>
              s ! akka.actor.Status.Success(wakeUp)
              log.debug(s"wakeup $wakeUp routed")
            case scala.util.Failure(failure) =>
              s ! akka.actor.Status.Failure(new Exception(s"Failure during wakeup $wakeUp routing", failure))
              log.warning(s"cannot route wakeup $wakeUp due to $failure")
          }
        case None =>
      }

    case event : DomainEvent => sender ! Failure(new MessageNotSupported(s"event bus cannot handle event $event"))

    case msg : RegisterHandlers[a] =>
      context.become(registering)
      self.forward(msg)

    case msg : RegisterSaga[s] =>
      context.become(registering)
      self.forward(msg)

    case ShutdownActor =>
      shutdownRequestedBy = sender()
      if (registerMessages.isEmpty) {
        context.stop(self)
        shutdownRequestedBy ! ShutdownActorCompleted(self)
      } else {
        context.become(shutdownInProgress)
      }

    case Terminated(actor) =>
      registerMessages find(_._1 == actor) match {
        case Some((_, register)) if !booting.contains(actor) =>
          self ! register
          log.warning(s"received Terminated from $actor. going to restart...")

        case None =>
          log.warning(s"received Terminated from $actor but not actor registered found")

        case _ =>
      }

    case msg@_ =>
      val errorText = s"EventBus received unexpected message $msg"
      sender ! Failure(new MessageNotSupported(errorText))
  }

  def registering: Receive = {
    case msg : RegisterHandlers[a] =>
      val s = sender()
      val office : ActorRef = Office.office[a](tenant)(msg.officeFactory)
      context.watch(office)
      registerMessages += office -> msg
      booting = booting :+ office

      msg.resolver.resolve().map {_ -> office}.foreach(handlers += _)

      val futures = handlers.values map {
        _.ask(UpdateHandlers(handlers))
      }

      Future.sequence(futures) onComplete {
        case scala.util.Success(results) =>
          booting = booting.filterNot(_ == office)
          s ! HandlersRegistered(handlers.keys)
          log.debug(s"aggregate $office registered")
          context.unbecome()
          unstashAll()

        case scala.util.Failure(failure) =>
          booting = booting.filterNot(_ == office)
          registerMessages -= office
          context.unwatch(office)
          handlers = handlers.filterNot(_._2 == office).toMap

          s ! akka.actor.Status.Failure(RegisterHandlersFailure(msg, failure))
          log.warning(s"cannot register handlers $msg due to $failure")

          context.unbecome()
          unstashAll()
      }

    case msg : RegisterSaga[s] =>
      val s = sender()

      val router = SagaRouter.router[s](tenant)(msg.routerFactory)
      context.watch(router)
      registerMessages += router -> msg
      booting = booting :+ router

      msg.resolver.resolve().map {_ -> router}.foreach(routers += _)

      msg.resolver.activateBy() match {
        case Some(m) =>
          val activateClass = m.asInstanceOf[Class[_ <: Activate]]
          val pair = activateClass -> router
          activates = activates - activateClass + pair
          log.debug(s"activator '$activateClass' for '$router' registered")
        case None =>
      }

      val wakeUpMessages = msg.resolver.wakeUpBy()
      wakeUpMessages foreach { m =>
        val wakeUpClass = m.asInstanceOf[Class[_ <: WakeUp]]
        val seq : Seq[ActorRef] = wakeUps.getOrElse[Seq[ActorRef]](wakeUpClass, Seq.empty).filterNot(r => r == router) :+ router
        val pair = wakeUpClass -> seq
        wakeUps = wakeUps - wakeUpClass + pair
        log.debug(s"wakeup '$wakeUpClass' for '$router' registered")
      }

      router.ask(UpdateHandlers(handlers))(timeout.duration) onComplete {
        case scala.util.Success(results) =>
          booting = booting.filterNot(_ == router)
          router.tell(GetSubscribedDomainEvents, s)
          log.debug(s"saga $router registered")

          context.unbecome()
          unstashAll()

        case scala.util.Failure(failure) =>
          registerMessages -= router
          booting = booting.filterNot(_ == router)
          context.unwatch(router)
          routers = routers.filterNot(_._2 == router).toMap
          wakeUps = wakeUps.filterNot(_._2 == router).toMap
          activates = activates.filterNot(_._2 == router).toMap

          s ! akka.actor.Status.Failure(RegisterSagaFailure(msg, failure))
          log.warning(s"cannot register saga $msg due to $failure")

          context.unbecome()
          unstashAll()

      }
    case m: Message =>
      log.debug(s"stashing message $m during registering")
      stash()
  }

    override def postStop() = {
    super.postStop()
    registerMessages.keys.foreach(context.unwatch)
  }

  var shutdownRequestedBy = ActorRef.noSender
  def shutdownInProgress: Receive = {
    case Terminated(actor) =>
      registerMessages -= actor
      if (registerMessages.isEmpty) {
        context.stop(self)
        shutdownRequestedBy ! ShutdownActorCompleted(self)
      }
  }
}
