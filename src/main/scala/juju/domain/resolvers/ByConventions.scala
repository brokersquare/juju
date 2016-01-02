package juju.domain.resolvers

import java.lang.reflect.Method

import akka.actor.Props
import juju.domain.AggregateRoot.{AggregateHandlersResolution, AggregateIdResolution}
import juju.domain.Saga.SagaHandlersResolution
import juju.domain.{AggregateRoot, AggregateRootFactory, Saga}
import juju.messages.{Activate, Command, DomainEvent, WakeUp}

import scala.reflect.ClassTag
import scala.reflect.runtime.{universe => ru}
import scala.util.{Failure, Success, Try}

object ByConventions {
  private def handleMethods[A : ClassTag](methodname: String) : Seq[Method] = implicitly[ClassTag[A]].runtimeClass.getDeclaredMethods
    .filter(_.getParameterTypes.length == 1)
    .filter(_.getName == methodname)

  implicit def aggregateHandlersResolution[A <: AggregateRoot[_] : ClassTag]() = new AggregateHandlersResolution[A] {
    override def resolve(): Seq[Class[_ <: Command]] = handleMethods[A]("handle")
      .map(_.getParameterTypes.head.asInstanceOf[Class[_ <: Command]])
      .filter(_ != classOf[Command])
  }

  implicit def factory[A <: AggregateRoot[_] : ClassTag]() = new AggregateRootFactory[A] {
    override def props: Props = Props(implicitly[ClassTag[A]].runtimeClass)
  }

  implicit def idResolution[A <: AggregateRoot[_] : ClassTag]() = new AggregateIdResolution[A] {
    override def resolve(command: Command): String = {
      val aggregateTypename = implicitly[ClassTag[A]].runtimeClass.getSimpleName

      val handlers = handleMethods("handle")
      val commandClass = command.getClass
      val handle = handlers.filter(_.getGenericParameterTypes.head == commandClass).head
      val annotation = handle.getDeclaredAnnotation[AggregateIdField](classOf[AggregateIdField])

      val fieldname = Option(annotation) match {
        case None =>
          val methodWithNameAggregateId = commandClass.getMethods.find(_.getName == s"${aggregateTypename}Id")
          val methodWithNameId = commandClass.getMethods.find(_.getName == "Id")
          (methodWithNameAggregateId getOrElse {
            methodWithNameId getOrElse {
              throw new IllegalArgumentException(s"Not provided AggregateIdField annotation for command '${commandClass.getSimpleName}'. Please set annotation or specify an id resolver for type '$aggregateTypename'")
            }
          }).getName
        case Some(a) => a.fieldname()
      }

      Try {
        commandClass.getMethod(fieldname)
      } match {
        case Success(method) => method.invoke(command).asInstanceOf[String]
        case Failure(e) => throw new NoSuchMethodError(s"Aggregate '$aggregateTypename' specifies not existing Aggregate id field '$fieldname' for command '${commandClass.getSimpleName}'")
      }
    }
  }

  implicit def sagaHandlersResolution[S <: Saga : ClassTag]() = new SagaHandlersResolution[S] {
    override def resolve(): Seq[Class[_ <: DomainEvent]] = handleMethods[S]("apply")
      .map(_.getParameterTypes.head.asInstanceOf[Class[_ <: DomainEvent]])
      .filter(_ != classOf[DomainEvent])
    override def wakeUpBy(): Seq[Class[_ <: WakeUp]] = handleMethods[S]("wakeup")
      .map(_.getParameterTypes.head.asInstanceOf[Class[_ <: WakeUp]])
      .filter(_ != classOf[WakeUp])
    override def activateBy() : Option[Class[_ <: Activate]] = None
  }
}