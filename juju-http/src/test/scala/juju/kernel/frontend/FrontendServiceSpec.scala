package juju.kernel.frontend

import akka.actor.Status.Success
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import juju.messages.Command
import org.scalatest.{FlatSpec, Matchers}
import rx.lang.scala.{Observable, Observer, Subscription}
import spray.http.{StatusCodes, HttpEntity, MediaTypes}
import spray.routing.{HttpService, Route}
import spray.testkit.ScalatestRouteTest
import scala.concurrent.duration._

//import spray.httpx.SprayJsonSupport._
//import spray.json.DefaultJsonProtocol._
import scala.reflect.ClassTag

object FrontendServiceSpec {
  case class FakeSimpleCommand(field1: String, field2: String) extends Command
  case class FakeSimpleWithIntParameterCommand(field1: String, field2: Int) extends Command


  private var archive = Seq.empty[Object]
  private var observers = Seq.empty[Observer[Object]]
  private val messages : Observable[Object] = {
    Observable.create[Object](observer => {
      observers = observers :+ observer
      Subscription {
        observer.onCompleted()
        observers = observers.filterNot (_ == observer)
      }
    })
  }

  def waitNextMessage(timeout : Duration) : Object = {
    messages.first.timeout(timeout).toBlocking.first
  }

  def notifyMessage(message : Object) = {
    archive = archive :+ message
    //println(s"message $message archived. Total archived ${archive.length}")
    observers foreach {
      o => o.onNext(message)
    }
  }
}

class FrontendServiceSpec extends FlatSpec with Matchers with ScalatestRouteTest with HttpService with FrontendService {
  behavior of "FrontendService"      //this will print the behavior of the test
  import FrontendServiceSpec._

  implicit def unmarshallerCommand[T <: Command : ClassTag] = Frontend.formUnmarshallerCommand
  def actorRefFactory = system // connect the DSL to the test ActorSystem

  override val apiRoute: Route = pathPrefix("api") {
    post {
      path("fake") {commandGatewayRoute[FakeSimpleCommand]} ~ path("fake") {commandGatewayRoute[FakeSimpleWithIntParameterCommand]}
    }
  }


  override val commandGateway: ActorRef = system.actorOf(Props(new Actor with ActorLogging {
    override def receive: Receive = {
      case m: Object =>
        sender() ! Success()
        notifyMessage(m)
    }
  }))

  it should "builds and routes the command when the POST has been called" in {

    val data = HttpEntity(MediaTypes.`application/x-www-form-urlencoded`, """field1=pippo&field2=pluto""")
    Post("/api/fake", data) ~> apiRoute ~> check {
      //println(s"checking messages... archive length is ${archive.length} ")
      val m = if (archive.isEmpty) waitNextMessage(10 seconds) else archive.last
      val res = responseAs[String]

      handled shouldBe true
      status shouldBe a [StatusCodes.Success]
      m shouldBe a [FakeSimpleCommand]
      val cmd = m.asInstanceOf[FakeSimpleCommand]
      cmd.field1 shouldEqual "pippo"
      cmd.field2 shouldEqual "pluto"
    }
  }

/*
  it should "unmarshall double fields" in {
    assert(false, "not yet implemented")
  }

  it should "unmarshall int fields" in {
    assert(false, "not yet implemented")
  }

  it should "unmarshall date fields" in {
    assert(false, "not yet implemented")
  }

    it should "returns an error when the commandGateway ask goes in timeout" in { //define returned status code???
    assert(false, "not yet implemented")
  }


  it should "returns invalid url when call the command route not in POST" in { //define returned status code???
    assert(false, "not yet implemented")
  }


  it should "returns an error when call not registered command route " in { //define returned status code???
    assert(false, "not yet implemented")
  }


  it should "returns an error when call command route with invalid parameters" in { //define returned status code???
    assert(false, "not yet implemented")
  }
*/
}


/*
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
*/

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