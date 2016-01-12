package juju.kernel.frontend
/*
import akka.actor.Actor
import org.scalatest.FlatSpec
import spray.routing.HttpService
import spray.test

class PingServiceTestKit extends FlatSpec with Specs2RouteTest


trait PingServiceActor extends PingService with Actor {
  def actorRefFactory = context
  def receive = runRoute(rootRoute)
}


trait PingService extends HttpService {
  def pingRoute = path("ping") {
    get {complete("pong!")}
  }

  def pongRoute = path("pong") {
    get {complete("pong?!?!")}
  }


  def rootRoute = pingRoute ~ pongRoute
}

*/