package juju.kernel.frontend

import org.scalatest.{Matchers, FlatSpec}
import spray.routing.HttpService
import spray.testkit.ScalatestRouteTest
import spray.http.StatusCodes._


class FullTestKitExampleSpec extends FlatSpec with Matchers with ScalatestRouteTest with HttpService {
  def actorRefFactory = system // connect the DSL to the test ActorSystem

  val smallRoute =
    get {
      pathSingleSlash {
        complete {
          <html>
            <body>
              <h1>Say hello to <i>spray</i>!</h1>
            </body>
          </html>
        }
      } ~
        path("ping") {
          complete("PONG!")
        }
    }

/*
  it should "return a greeting for GET requests to the root path" in {
    Get() ~> smallRoute ~> check {
      responseAs[String] should contain("Say hello")
    }
  }*/

  it should "return a 'PONG!' response for GET requests to /ping" in {
    Get("/ping") ~> smallRoute ~> check {
      responseAs[String] === "PONG!"
    }
  }

  it should "leave GET requests to other paths unhandled" in {
    Get("/kermit") ~> smallRoute ~> check {
      handled shouldBe false
    }
  }

  it should "return a MethodNotAllowed error for PUT requests to the root path" in {
    Put() ~> sealRoute(smallRoute) ~> check {
      status === MethodNotAllowed
      responseAs[String] === "HTTP method not allowed, supported methods: GET"
    }
  }

}


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