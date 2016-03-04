package juju.infrastructure

import akka.actor.{ReceiveTimeout, Actor, Props}

import scala.concurrent.duration.Duration

object Timer {
  case object StopTimer
  def props(timeout: Duration) : Props = {
    Props(classOf[Timer], timeout)
  }
}

class Timer(timeout: Duration) extends Actor {
  import Timer._
  context.setReceiveTimeout(timeout)

  override def receive: Actor.Receive = {
    case ReceiveTimeout =>
      context.stop(self)
    case StopTimer =>
      context.stop(self)
  }
}