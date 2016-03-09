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

import scala.concurrent.{ExecutionContext, Future}
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

  def retry[T](times: Int)(f: => Future[T])(implicit executor: ExecutionContext): Future[T] =
    f recoverWith { case _ if times > 0 => retry(times - 1)(f) }

  def waitForBoot: Actor.Receive = {
    case Boot =>
      def bootFuture = for {
          f1 <- registerHandlers(bus)
          f2 <- registerSagas(bus)
        } yield SystemIsUp(appname)

      val retryable = retry(5)(bootFuture)

      (pipe(retryable) to sender).future .onComplete {
        case scala.util.Success(up) =>
          context.become(receive)
          unstashAll()

          scheduler = schedulerFactory.create(tenant, backendConfig.role, context)
          activations.toSet[SendActivation].foreach(scheduler ! _)
          wakeups.toSet[ScheduleWakeUp].foreach(scheduler ! _)

          log.info(s"Backend $appname bootstrap succeeded")

        case scala.util.Failure(cause) =>
          log.warning(s"Backend $appname bootstrap failed due to error: $cause")
          throw new Error(s"Max number of retry during the backend $appname boot reached")
      }

    case _ => stash()
  }

  context.become(waitForBoot)

  override def receive: Receive = {
    case juju.messages.ShutdownActor =>
      bus ! juju.messages.ShutdownActor
      context.become(shutdownInProgress)
      log.info(s"$appname receive shutdown request")
    case m @ _ =>
      log.warning(s"$appname receive message $m")
  }

  def shutdownInProgress: Receive = {
    case juju.messages.ShutdownActorCompleted(`bus`) =>
      bus ! juju.messages.ShutdownActor
      context.stop(self)
      log.debug(s"$appname stopped bus. Closing in progress...")

    case m @ _ =>
      log.warning(s"$appname receive message $m during shutdown")
  }

  private def registerHandlers(bus: ActorRef): Future[Seq[HandlersRegistered]] = {
    Future.sequence(aggregates.toSeq map (m => (bus ? m) map (_.asInstanceOf[HandlersRegistered])))
  }

  private def registerSagas(bus: ActorRef): Future[Seq[DomainEventsSubscribed]] = {
    Future.sequence(sagas.toSeq map (m => (bus ? m) map (_.asInstanceOf[DomainEventsSubscribed])))
  }
}