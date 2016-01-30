package juju.kernel.backend

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.pattern._
import juju.domain.AggregateRoot.AggregateHandlersResolution
import juju.domain.Saga.SagaHandlersResolution
import juju.domain.{AggregateRoot, Saga}
import juju.infrastructure.AppScheduler.{ScheduleWakeUp, SendActivation}
import juju.infrastructure._
import juju.messages.{Activate, SystemIsUp, WakeUp}

import scala.concurrent.Future
import scala.concurrent.duration._

trait Backend extends Actor with ActorLogging with Stash with Node {
  backendConfig: BackendConfig =>

  import juju.messages.Boot

  implicit def system : ActorSystem = context.system
  implicit def dispatcher = system.dispatcher // The ExecutionContext that will be used

  def config = system.settings.config
  implicit lazy val timeout: akka.util.Timeout = config getDuration("juju.timeout",TimeUnit.SECONDS) seconds

  lazy val bus = context.actorOf(EventBus.props(), EventBus.actorName(tenant))

  private var aggregates : Set[RegisterHandlers[_]] = Set.empty
  protected def registerAggregate[A <: AggregateRoot[_] : OfficeFactory : AggregateHandlersResolution]() = {
    aggregates = aggregates + RegisterHandlers[A]
  }

  private var sagas : Set[RegisterSaga[_]] = Set.empty
  protected def registerSaga[S <: Saga : SagaRouterFactory : SagaHandlersResolution]() = {
    sagas = sagas + RegisterSaga[S]
  }

  private var activations: Seq[SendActivation] = Seq.empty
  protected def registerActivation[A <: Activate](activate: A): Unit = {
    activations = activations :+ SendActivation(activate)
  }

  private var wakeups: Seq[_ <: ScheduleWakeUp] = Seq.empty
  protected def scheduleWakeUp[W <: WakeUp](wakeUp: W, initialDelay: FiniteDuration, interval: FiniteDuration): Unit = {
    wakeups = wakeups :+ ScheduleWakeUp(wakeUp, initialDelay, interval)
  }
  private var scheduler = ActorRef.noSender

  def waitForBoot: Actor.Receive = {
    case Boot =>
      val future = for {
        f1 <- registerHandlers(bus)
        f2 <- registerSagas(bus)
      } yield SystemIsUp(appname)

      (pipe(future) to sender).future.map(up => {
        context.become(receive)
        unstashAll()

        scheduler = schedulerFactory.create(tenant, backendConfig.role, context)
        activations.toSet[SendActivation].foreach(scheduler ! _)
        wakeups.toSet[ScheduleWakeUp].foreach(scheduler ! _)
      })
    case _ => stash()
  }

  context.become(waitForBoot)

  override def receive: Receive = {
    case m @ _ =>
      log.debug(s"$appname receive message $m")
  }

  override def postStop() : Unit =  {
    bus ! PoisonPill
  }

  private def registerHandlers(bus: ActorRef): Future[Seq[HandlersRegistered]] = {
    Future.sequence(aggregates.toSeq map (m => (bus ? m) map (_.asInstanceOf[HandlersRegistered])))
  }

  private def registerSagas(bus: ActorRef): Future[Seq[DomainEventsSubscribed]] = {
    Future.sequence(sagas.toSeq map (m => (bus ? m) map (_.asInstanceOf[DomainEventsSubscribed])))
  }
}