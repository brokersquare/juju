package juju.infrastructure.local

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.util.Timeout
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain._
import juju.infrastructure.{DomainEventsSubscribed, GetSubscribedDomainEvents, SagaRouterFactory, UpdateHandlers}
import juju.messages.{Activate, Command, DomainEvent, WakeUp}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object LocalSagaRouter {
  implicit def localSagaRouterFactory[S <: Saga : ClassTag :SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](implicit system : ActorSystem) = new SagaRouterFactory[S] {
    private def log = system.log


    override def getOrCreate: ActorRef = {
      val actorName = s"$routerName"
      implicit val timeout = Timeout(FiniteDuration(10, TimeUnit.SECONDS))
      val props = Props(new LocalSagaRouter[S]())
      Try(system.actorOf(props, actorName)) match { //TODO: make async
        case Success(ref) =>
          ref
        case Failure(ex) =>
          log.debug(s"fails to create the router: try to find as child")
          log.warning(s"${ex.getMessage}")
          val childPath = system.child(actorName)
          log.debug(s"looking for router $childPath")
          val childFuture : Future[ActorRef] = system.actorSelection(childPath).resolveOne()
          Await.ready(childFuture, timeout.duration).value.get match {
            case Success(res) => res
            case Failure(ex) =>
              log.warning(s"an error occours ${ex.getMessage}. Retrying to get or create ...")
              getOrCreate
              //throw ex
          }
        }
    }
  }
}

class LocalSagaRouter[S <: Saga](implicit ct: ClassTag[S], handlersResolution: SagaHandlersResolution[S], idResolver : SagaCorrelationIdResolution[S], sagaFactory : SagaFactory[S])
  extends Actor with ActorLogging {
  val sagaName = implicitly[ClassTag[S]].runtimeClass.getSimpleName //TODO: take it from the sagaRouterFactory
  var handlers = Map[Class[_ <: Command], ActorRef]()
  val subscribedEvents = handlersResolution.resolve()
  
  override def preStart() = {
    subscribedEvents foreach {
      h => context.system.eventStream.subscribe(self, h)
    }
  }

  override def receive: Receive = {
    case e : GetSubscribedDomainEvents => sender ! DomainEventsSubscribed(subscribedEvents)
    case e : Activate =>
      log.debug(s"received activate $e message")
      getOrCreateSaga(e.correlationId)
    case e : WakeUp =>
      context.children foreach { child =>
        log.debug(s"routing wake up event $e to $child")
        child ! e
      }
    case e : DomainEvent =>
      idResolver resolve e match {
        case Some(correlationId) =>
          log.debug(s"received domain event '$e' with correlation '$correlationId'")
          val sagaRef = getOrCreateSaga(correlationId)
          sagaRef ! e
        case None =>
      }
    case c : Command =>
      val matchedHandlers = handlers.filter(_._1 == c.getClass)
      matchedHandlers.foreach(_._2 ! c)
      if (matchedHandlers.isEmpty) {akka.actor.Status.Failure(new Exception(s"no handlers for command ${c.getClass}"))} //TODO: manage errors during dispatch (a queue for not sended message? A resend from sender)
    case UpdateHandlers(h) =>
      handlers = h
      sender ! akka.actor.Status.Success
    case _ => ???
  }

  private def getOrCreateSaga(correlationId: String): ActorRef = {
    context.child(correlationId).getOrElse({
      val ref = context.actorOf(sagaFactory.props(correlationId, self), correlationId)
      log.debug(s"router $self create saga $ref")
      ref
    })
  }
}