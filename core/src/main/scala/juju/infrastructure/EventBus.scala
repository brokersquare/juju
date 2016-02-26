package juju.infrastructure

import akka.actor.Status.Failure
import akka.actor._
import akka.pattern.ask
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
case class MessageNotSupported(text: String) extends Exception(text)
case class UpdateHandlers(handlers : Map[Class[_ <: Command], ActorRef]) extends InfrastructureMessage

case class RegisterHandlers[A <: AggregateRoot[_]](implicit val officeFactory: OfficeFactory[A], val resolver: AggregateHandlersResolution[A]) extends InfrastructureMessage
case class HandlersRegistered(handlers : Iterable[Class[_ <: Command]]) extends InfrastructureMessage
case class RegisterSaga[S <: Saga](implicit val routerFactory : SagaRouterFactory[S], val resolver: SagaHandlersResolution[S]) extends InfrastructureMessage
case object GetSubscribedDomainEvents extends InfrastructureMessage
case class DomainEventsSubscribed(events: Iterable[Class[_ <: DomainEvent]]) extends InfrastructureMessage

class EventBus(tenant: String) extends Actor with ActorLogging with Stash {
  var activates = Map[Class[_ <: Activate], ActorRef]()
  var handlers = Map[Class[_ <: Command], ActorRef]()
  var wakeUps = Map[Class[_ <: WakeUp], Seq[ActorRef]]()
  implicit val timeout = Timeout(5 seconds) //TODO: pass as parameter to the eventbus
  implicit val executeContext = context.dispatcher

  log.debug("EventBus is up and running")

  override def receive: Receive = {

    case command : Command =>
      log.debug(s"eventbus command $command received")

      val commandType = command.getClass

      handlers.get(commandType) match {
        case Some(office) =>
          office ! command
          sender ! akka.actor.Status.Success(command)
        case None =>
          sender ! akka.actor.Status.Failure(HandlerNotDefinedException)
      }

    case activate : Activate =>
      log.debug(s"activator $activate requested")
      activates.get(activate.getClass) match {
        case Some(destRef) => destRef ! activate
        case None => 
      }

    case wakeUp : WakeUp =>
      log.debug(s"wakeup $wakeUp requested")
      val wakeUpClass = wakeUp.getClass
      wakeUps.get(wakeUpClass) match {
        case Some(destRefs) => destRefs foreach {
          ref => {
              ref ! wakeUp
            }
        }
        case None =>
      }

    case event : DomainEvent => sender ! Failure(new MessageNotSupported(s"event bus cannot handle event $event"))

    case msg : RegisterHandlers[a] =>
      val s = sender()
      //TODO: check if handler already set to a different ref and if yes error
      val office : ActorRef = Office.office[a](tenant)(msg.officeFactory)
      //TODO: monitor the office through DeathWatch
      val pairs = msg.resolver.resolve().map(c => c -> office)
      handlers = List(handlers.toList, pairs.toList).flatten.toMap
      val futures = handlers.values map {
        _.ask(UpdateHandlers(handlers))
      }

      Future.sequence(futures).onSuccess {
        case results =>
          s ! HandlersRegistered(handlers.keys)
          log.debug(s"aggregate $office registered")
      }

    case msg : RegisterSaga[s] =>
      val s = sender()

      val routerRef = SagaRouter.router[s](tenant)(msg.routerFactory)

      msg.resolver.activateBy() match {
        case Some(m) =>
          val activateClass = m.asInstanceOf[Class[_ <: Activate]]
          val pair = activateClass -> routerRef
          activates = activates - activateClass + pair
          log.debug(s"activator '$activateClass' for '$routerRef' registered")
        case None =>
      }

      val wakeUpMessages = msg.resolver.wakeUpBy()
      wakeUpMessages foreach { m =>
          val wakeUpClass = m.asInstanceOf[Class[_ <: WakeUp]]
          val seq : Seq[ActorRef] = wakeUps.getOrElse[Seq[ActorRef]](wakeUpClass, Seq.empty).filterNot(r => r == routerRef) :+ routerRef
          val pair = wakeUpClass -> seq
          wakeUps = wakeUps - wakeUpClass + pair
          log.debug(s"wakeup '$wakeUpClass' for '$routerRef' registered")
      }

      routerRef.ask(UpdateHandlers(handlers)).onSuccess {
        case results =>
          routerRef.tell(GetSubscribedDomainEvents, s)
          log.debug(s"saga $routerRef registered")
      }
    case msg@_ =>
      val errorText = s"EventBus received unexpected message $msg"
      sender ! Failure(new MessageNotSupported(errorText))
  }
}
