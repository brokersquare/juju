package juju.kernel.frontend

import akka.actor.{Actor, ActorLogging, ActorRef}
import juju.messages.Command
import spray.http.{HttpEntity, MediaTypes}
import spray.httpx.unmarshalling.Unmarshaller
import spray.routing.{HttpService, Route}

import scala.reflect.ClassTag
import scala.reflect.runtime.{universe => ru}


object Frontend {
  implicit def formUnmarshallerCommand[T <: Command : ClassTag] = Unmarshaller[T](MediaTypes.`application/x-www-form-urlencoded`) {
    case e: HttpEntity.NonEmpty => {
      val u = spray.httpx.unmarshalling.FormDataUnmarshallers.UrlEncodedFormDataUnmarshaller(e)
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

}

trait Frontend extends Actor with ActorLogging with FrontendService {
  def actorRefFactory = context
  implicit def unmarshallerCommand[T <: Command : ClassTag] = Frontend.formUnmarshallerCommand
  def receive = runRoute(apiRoute)
}

trait FrontendService extends HttpService {
  val apiRoute: Route
  val commandGateway : ActorRef

  protected def commandGatewayRoute[C <: Command : ClassTag : Unmarshaller] = {
    import akka.pattern.ask

    import scala.concurrent.duration._
    implicit val timeout: akka.util.Timeout = 5 seconds
    implicit def ec = actorRefFactory.dispatcher

    entity(as[C]) { command =>
      complete {
        (commandGateway ? command).map { _ =>
          s"command '$command' sent"
        }
      }
    }
  }
}