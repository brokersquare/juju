package juju.domain.resolvers

import java.lang.annotation.Annotation
import java.lang.reflect.Method

import akka.actor.{ActorRef, Props}
import juju.domain.AggregateRoot.{AggregateHandlersResolution, AggregateIdResolution}
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain.{AggregateRoot, AggregateRootFactory, Saga, SagaFactory}
import juju.messages.{Activate, Command, DomainEvent, WakeUp}

import scala.annotation.tailrec
import scala.reflect.ClassTag
import scala.reflect.runtime.{universe => ru}
import scala.util.{Failure, Success, Try}

object ByConventions {

  private def getMethods[A : ClassTag](methodname: String) : Seq[Method] = {
    val clazz = implicitly[ClassTag[A]].runtimeClass

    @tailrec def loop(c: Class[_], methods: Seq[Method]): Seq[Method] = {
      val m: Seq[Method] = c.getDeclaredMethods
        .filter(_.getParameterTypes.length == 1)
        .filter(_.getName == methodname) ++ methods

      c.getSuperclass match {
        case s : Class[_] if s == classOf[Object] => m
        case s => loop(s, m)
      }
    }

    loop(clazz, List.empty)
  }

  implicit def aggregateHandlersResolution[A <: AggregateRoot[_] : ClassTag]() = new AggregateHandlersResolution[A] {
    override def resolve(): Seq[Class[_ <: Command]] = getMethods[A]("handle")
      .map(_.getParameterTypes.head.asInstanceOf[Class[_ <: Command]])
      .filter(_ != classOf[Command])
  }

  implicit def aggregateFactory[A <: AggregateRoot[_] : ClassTag]() = new AggregateRootFactory[A] {
    override def props: Props = Props(implicitly[ClassTag[A]].runtimeClass)
  }

  implicit def aggregateIdResolution[A <: AggregateRoot[_] : ClassTag]() = new AggregateIdResolution[A] {
    override def resolve(command: Command): String = {
      val aggregateTypename = implicitly[ClassTag[A]].runtimeClass.getSimpleName

      val handlers = getMethods("handle")
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
    override def resolve(): Seq[Class[_ <: DomainEvent]] = getMethods[S]("apply")
      .map(_.getParameterTypes.head.asInstanceOf[Class[_ <: DomainEvent]])
      .filter(_ != classOf[DomainEvent])
 
    override def wakeUpBy(): Seq[Class[_ <: WakeUp]] = getMethods[S]("wakeup")
      .map(_.getParameterTypes.head.asInstanceOf[Class[_ <: WakeUp]])
      .filter(_ != classOf[WakeUp])
   
    override def activateBy() : Option[Class[_ <: Activate]] = {
      val sagaClass = implicitly[ClassTag[S]].runtimeClass

      @tailrec def loop(c: Class[_]): Option[Annotation] = {
        val activate = c.getDeclaredAnnotations.find(_.annotationType() == classOf[ActivatedBy])
        activate match {
          case Some(a) => activate
          case _ => c.getSuperclass match {
            case s : Class[_] if s == classOf[Object] => None
            case s => loop(s)
          }
        }
      }

      val activate = loop(sagaClass)

      val t = activate.map (_.asInstanceOf[ActivatedBy].message)

      val res = if (t.isDefined && classOf[Activate].isAssignableFrom(t.get)) {
        Some(t.get).asInstanceOf[Option[Class[_ <: Activate]]]
      } else {
        None.asInstanceOf[Option[Class[_ <: Activate]]]
      }
      res
    }
  }

  implicit def sagaFactory[S <: Saga : ClassTag]() = new SagaFactory[S] {
    override def props(correlationId: String, commandRouter: ActorRef): Props = Props(implicitly[ClassTag[S]].runtimeClass, correlationId, commandRouter)
  }

  implicit def correlationIdResolution[S <: Saga : ClassTag]() = new SagaCorrelationIdResolution[S] {
    /**
     * resolve returns an error if the event haven't to be handled (signal a wrong routing logic) otherwise returns None if a handled event has specific condition, otherwise it returns the correlationid
     */
    override def resolve(event: DomainEvent): Option[String] = {
      val sagaTypename = implicitly[ClassTag[S]].runtimeClass.getSimpleName

      val handlers = getMethods("apply")
      val eventClass = event.getClass
      val handle = handlers.filter(_.getGenericParameterTypes.head == eventClass).head
      val annotation = handle.getDeclaredAnnotation[CorrelationIdField](classOf[CorrelationIdField])

      val fieldname = Option(annotation) match {
        case None =>
          val methodWithNameCorrelationId = eventClass.getMethods.find(_.getName == "CorrelationId")
          val methodWithNameId = eventClass.getMethods.find(_.getName == "Id")
          (methodWithNameCorrelationId getOrElse {
            methodWithNameId getOrElse {
              throw new IllegalArgumentException(s"Not provided CorrelationIdField annotation for event '${eventClass.getSimpleName}'. Please set annotation or specify a correlation id resolver for type '$sagaTypename'")
            }
          }).getName
        case Some(a) => a.fieldname()
      }

      Some(Try {
        eventClass.getMethod(fieldname)
      } match {
        case Success(method) => method.invoke(event).asInstanceOf[String]
        case Failure(e) => throw new NoSuchMethodError(s"Saga '$sagaTypename' specifies not existing Correlation id field '$fieldname' for event '${eventClass.getSimpleName}'")
      })
    }
  }
}