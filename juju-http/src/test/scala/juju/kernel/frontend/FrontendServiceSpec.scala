package juju.kernel.frontend

import akka.actor._
import akka.http.scaladsl.model.{HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.ActorMaterializer
import juju.infrastructure.CommandProxyFactory
import juju.kernel.frontend.FrontendServiceSpec.{FakeSimpleCommand, FakeSimpleWithNumberParameterCommand}
import juju.messages.Command
import org.scalatest.{FlatSpec, Matchers}
import rx.lang.scala.{Observable, Observer, Subscription}
import spray.json.DefaultJsonProtocol

import scala.concurrent.duration._

object FrontendServiceSpec {
  case class FakeSimpleCommand(field1: String, field2: String) extends Command
  case class FakeSimpleWithNumberParameterCommand(field1: String, field2: Double) extends Command

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
    observers foreach {
      o => o.onNext(message)
    }
  }
}

object CommandsJsonProtocol extends DefaultJsonProtocol {
  implicit val fakeSimpleCommandFormat = jsonFormat(FakeSimpleCommand, "field1", "field2")
  implicit val fakeSimpleWithDoubleParameterCommand = jsonFormat(FakeSimpleWithNumberParameterCommand, "field1", "field2")
}


class FrontendServiceSpec extends FlatSpec with Matchers with ScalatestRouteTest with FrontendService with PingService  {
  import CommandsJsonProtocol._
  import FrontendServiceSpec._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  implicit val actorMaterializer = ActorMaterializer()
  implicit override val ec = scala.concurrent.ExecutionContext.global

  override def commandProxyFactory: CommandProxyFactory =  new FakeCommandProxyFactory(system)

  override def commandApiRoute = pathPrefix("api") {
    rootRoute ~ post {
      commandGatewayRoute[FakeSimpleCommand] ~ commandGatewayRoute[FakeSimpleWithNumberParameterCommand]
    }
  }

  it should "be ping" in {
    Get("/api/ping") ~> commandApiRoute ~> check {
      status shouldBe a [StatusCodes.Success]
      handled shouldBe true
      responseAs[String] shouldEqual "pong!"
    }
  }

  it should "builds and routes the command when the json POST has been called" in {
    val data = HttpEntity(MediaTypes.`application/json`, """{"field1":"pippo","field2":"pluto"}""")
    Post("/api/fake", data) ~> commandApiRoute ~> check {
      handled shouldBe true
      status shouldBe a [StatusCodes.Success]

      val m = if (archive.isEmpty) waitNextMessage(10 seconds) else archive.last
      val res = responseAs[String]

      m shouldBe a [FakeSimpleCommand]
      val cmd = m.asInstanceOf[FakeSimpleCommand]
      cmd.field1 shouldEqual "pippo"
      cmd.field2 shouldEqual "pluto"
    }
  }

  /*
  it should "builds and routes the command when the form POST has been called" in {
    implicit val unmarshaller = Frontend.formUnmarshallerCommand[FakeSimpleCommand]
    val data = HttpEntity(MediaTypes.`application/x-www-form-urlencoded`, """field1=pippo&field2=pluto""")
    Post("/api/fake", data) ~> apiRoute ~> check {
      handled shouldBe true
      status shouldBe a [StatusCodes.Success]

      val m = if (archive.isEmpty) waitNextMessage(10 seconds) else archive.last
      val res = responseAs[String]

      m shouldBe a [FakeSimpleCommand]
      val cmd = m.asInstanceOf[FakeSimpleCommand]
      cmd.field1 shouldEqual "pippo"
      cmd.field2 shouldEqual "pluto"
    }
  }
*/

  it should "unmarshall double fields" in {
    val data = HttpEntity(MediaTypes.`application/json`, """{"field1":"pippo","field2":1.1}""")
    Post("/api/fake", data) ~> commandApiRoute ~> check {
      handled shouldBe true
      status shouldBe a [StatusCodes.Success]

      val m = if (archive.isEmpty) waitNextMessage(10 seconds) else archive.last
      val res = responseAs[String]

      m shouldBe a [FakeSimpleWithNumberParameterCommand]
      val cmd = m.asInstanceOf[FakeSimpleWithNumberParameterCommand]
      cmd.field1 shouldEqual "pippo"
      cmd.field2 shouldEqual 1.1
    }
  }

  it should "unmarshall int fields" in {
    val data = HttpEntity(MediaTypes.`application/json`, """{"field1":"pippo","field2":1}""")
    Post("/api/fake", data) ~> commandApiRoute ~> check {
      handled shouldBe true
      status shouldBe a [StatusCodes.Success]

      val m = if (archive.isEmpty) waitNextMessage(10 seconds) else archive.last
      val res = responseAs[String]

      m shouldBe a [FakeSimpleWithNumberParameterCommand]
      val cmd = m.asInstanceOf[FakeSimpleWithNumberParameterCommand]
      cmd.field1 shouldEqual "pippo"
      cmd.field2 shouldEqual 1
    }
  }
  /*
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

class FakeCommandProxyFactory(system : ActorSystem) extends CommandProxyFactory {
  override def actor: ActorRef = system.actorOf(Props(new Actor with ActorLogging {
    override def receive: Receive = {
      case m: Object =>
        sender() ! akka.actor.Status.Success(m)
        FrontendServiceSpec.notifyMessage(m)
    }
  }))
}
