package juju.infrastructure

import akka.actor._
import juju.infrastructure.AppScheduler.{ScheduleWakeUp, SendActivation}
import juju.messages.{WakeUp, Activate}

import scala.concurrent.duration.FiniteDuration

trait AppSchedulerFactory {
  def create(tenant: String, role: String, context: ActorContext) : ActorRef
}

object AppScheduler {
  case class SendActivation(activate: Activate)
  case class ScheduleWakeUp(wakeup: WakeUp, initialDelay: FiniteDuration, interval: FiniteDuration)
}

class AppScheduler(tenant: String) extends Actor with ActorLogging with Stash {
  private case object FindBus

  val busName = s"${EventBus.actorName(tenant)}"
  val busSelections = (s"/user/$busName" :: s"/*/user/*/$busName*" :: s"/user/*/$busName*" :: Nil).map(context.actorSelection)
  var bus = ActorRef.noSender
  context become waitForSync
  self ! FindBus

  def waitForSync: Receive = {
    case FindBus =>
      busSelections foreach (_ ! Identify(None))
    case ActorIdentity(_, Some(actorRef)) =>
      bus = actorRef
      context watch bus
      context.unbecome()
      unstashAll()
    case ActorIdentity(_, None) => // not alive
    case _ =>
      stash()
  }

  private var activations: Seq[SendActivation] = Seq.empty
  private var wakeups: Seq[_ <: ScheduleWakeUp] = Seq.empty

  override def receive: Receive = {
    case m@SendActivation(activation) if !activations.contains(m) =>
      bus ! activation
      activations = activations :+ m
    case m@ScheduleWakeUp(wakeup, initialDelay, interval) if !wakeups.contains(m) =>
      implicit val ec = context.system.dispatcher
      context.system.scheduler.schedule(initialDelay, interval, publishWakeUpRunnable(wakeup))
      wakeups = wakeups :+ m
    case Terminated(r) if r == bus =>
      context become waitForSync
      self ! FindBus
  }

  def publishWakeUpRunnable(message: WakeUp): Runnable = {
    new Runnable {
      override def run(): Unit = {
        bus ! message
      }
    }
  }
}