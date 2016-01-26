package juju.kernel.frontend

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import juju.infrastructure.CommandProxyFactory
import juju.messages.Command

import scala.concurrent.ExecutionContext


/*
import akka.actor.{Actor, ActorLogging}
import juju.messages.Command
import spray.json._

import scala.reflect.ClassTag
import scala.reflect.runtime.{universe => ru}

object Frontend extends DefaultJsonProtocol {
/*
  def formUnmarshallerCommand[T <: Command : ClassTag] = Unmarshaller[T](MediaTypes.`application/x-www-form-urlencoded`) {
    case e: HttpEntity.NonEmpty => {
      val u = akka.http.FormDataUnmarshallers.UrlEncodedFormDataUnmarshaller(e)
      u match {
        case Right(data) => {
          val commandType = implicitly[ClassTag[T]].runtimeClass
          val map: Map[String, String] = data.fields.toMap

          val properties = getCaseClassParameters[T]

          val parameters = properties.map { p =>
            val fieldname = p._1.toString
            //TODO:add type check and conversion
            map.getOrElse(fieldname, () => throw new IllegalArgumentException(s"cannot construct command ${commandType.getSimpleName} due to missing parameter $fieldname"))
          }

          val constructor = commandType.getConstructors.head
          val command = constructor.newInstance(parameters: _*)
          command.asInstanceOf[T]
        }
        case Left(ex) => ???
      }
    }
  }

  private def companionMembers(clazzTag: scala.reflect.ClassTag[_]): ru.MemberScope = {
    val runtimeClass = clazzTag.runtimeClass
    val rootMirror = ru.runtimeMirror(runtimeClass.getClassLoader)
    val classSymbol = rootMirror.classSymbol(runtimeClass)
    // get the companion here
    classSymbol.companion.typeSignature.members
  }

  private def getCaseClassParameters[T : ClassTag] :Seq[(ru.Name, ru.Type)]  =
    companionMembers(scala.reflect.classTag[T])
      .filter { m => m.isMethod && m.name.toString == "apply"}
      .head.asMethod.paramLists.head.map(p => (p.name.decodedName, p.info)).toSeq
*/
}

trait CommandUnmarshaller {
  def unmarshallerCommand[T <: Command : ClassTag]() : Unit
}

trait Frontend extends Actor with ActorLogging /*with FrontendService*/ with PingService {
  def actorRefFactory = context
  //implicit def unmarshallerCommand[T <: Command : ClassTag] = Frontend.formUnmarshallerCommand
  // def receive = runRoute(apiRoute ~ pingRoute)
}
/*
trait FrontendService {
  self: CommandUnmarshaller =>

  val apiRoute: Route

  def commandProxyFactory : CommandProxyFactory
  private def commandGateway() = commandProxyFactory.actor

  protected def commandGatewayRoute[C <: Command : Unmarshaller] = {
    import akka.pattern.ask

    import scala.concurrent.duration._
    implicit val timeout: akka.util.Timeout = 5 seconds
    implicit def unmarshaller() = self.unmarshallerCommand()
    //implicit def ec = actorRefFactory.dispatcher

    entity(as[C]) { command =>
      complete {
        (commandGateway() ? command).map { _ =>
          s"command '$command' sent"
        }
      }
    }
  }
}
*/
*/
/*
trait Frontend extends Actor with ActorLogging with FrontendService with PingService {
  def actorRefFactory = context
  //implicit def unmarshallerCommand[T <: Command : ClassTag] = Frontend.formUnmarshallerCommand
   def receive = runRoute(apiRoute ~ pingRoute)
}*/

trait FrontendService {

  val apiRoute: Route
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