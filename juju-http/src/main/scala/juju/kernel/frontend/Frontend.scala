package juju.kernel.frontend

import akka.actor.{Stash, ActorLogging, Actor}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import juju.infrastructure.CommandProxyFactory
import juju.messages.{SystemIsUp, Boot, Command}

import scala.concurrent.ExecutionContext

abstract class Frontend extends Actor with ActorLogging with FrontendService with PingService with Stash {
  import akka.stream.ActorMaterializer

  def appname: String
  def apiPrefix: String = "api"

  implicit val materializer = ActorMaterializer()

  val commandProxyFactory: CommandProxyFactory
  override implicit val ec: ExecutionContext = context.dispatcher

  implicit def s = context.system
  def config = s.settings.config

  def host = config.getString("service.host")
  def port = config.getInt("service.port")

  private val apiRoute = if (apiPrefix != null && apiPrefix != "") {
    pathPrefix("api") {
      post {
        commandApiRoute
      } ~
        get {
          rootRoute
        }
    }
  } else {
    post {
      commandApiRoute
    } ~
      get {
        rootRoute
      }
  }

  def waitForBoot: Actor.Receive = {
    case Boot =>
      val booter = sender()
      Http().bindAndHandle(apiRoute, host, port).onSuccess {
        case _  =>
          log.info(s"Http server up and running on $host:$port")
          context.become(receive)
          unstashAll()
          booter ! SystemIsUp(appname)
      }
    case _ => stash()
  }

  context.become(waitForBoot)

  override def receive: Receive = {
    case m @ _ =>
      log.debug(s"$appname receive message $m")
  }
}

trait FrontendService {
  def commandApiRoute: Route
  implicit val ec: ExecutionContext

  def commandProxyFactory : CommandProxyFactory
  private def commandGateway() = commandProxyFactory.actor

  protected def commandGatewayRoute[C <: Command : FromRequestUnmarshaller] = {
    import akka.pattern.ask

    import scala.concurrent.duration._
    implicit val timeout: akka.util.Timeout = 5 seconds

    entity(as[C]) { command =>
      complete {
        (commandGateway() ? command).map { _ =>
          s"command '$command' sent"
        }
      }
    }
  }
}

trait PingService {
  def pingRoute = path("ping") {
    get {complete("pong!")}
  }

  def pongRoute = path("pong") {
    get {complete("pong?!?!")}
  }

  def rootRoute = pingRoute ~ pongRoute
}


//http://stackoverflow.com/questions/25178108/converting-datetime-to-a-json-string
/*
You have several problems here.

First, the toString() method in AbstractDateTime requires one or several arguments see here.

But I would advise you against this path and recommend using properly Spray-Json.

Spray-json does not know how to serialize Option[DateTime], therefore you have to provide a RootJsonFormat for it.

This is what I am doing.
*//*
implicit object DateJsonFormat extends RootJsonFormat[DateTime] {

  private val parserISO : DateTimeFormatter = ISODateTimeFormat.dateTimeNoMillis()

  override def write(obj: DateTime) = JsString(parserISO.print(obj))

  override def read(json: JsValue) : DateTime = json match {
    case JsString(s) => parserISO.parseDateTime(s)
    case _ => throw new DeserializationException("Error info you want here ...")
  }
}*/
//Adapt it as you want if you do not want to use ISO formatting.
//FTR: There is also a spray.http.DateTime class that is more efficient that the alternatives and works great
//if you don't need timezone support or millisecond precision.