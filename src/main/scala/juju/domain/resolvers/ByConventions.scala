package juju.domain.resolvers

import akka.actor.Props
import juju.domain.{AggregateRootFactory, Handle, AggregateRoot}
import juju.domain.AggregateRoot.{AggregateIdResolution, AggregateHandlersResolution}
import juju.messages.Command
import scala.reflect.runtime.{universe => ru}

import scala.reflect.ClassTag

object ByConventions {
  implicit def handlersResolution[A <: AggregateRoot[_] : ClassTag]() = new AggregateHandlersResolution[A] {
    override def resolve(): Seq[Class[_ <: Command]] = implicitly[ClassTag[A]].runtimeClass.getDeclaredMethods
      .filter(_.getParameterTypes.length == 1)
      .filter(_.getName == classOf[Handle[_ <: Command]].getMethods.head.getName)
      .map(_.getParameterTypes.head.asInstanceOf[Class[_ <: Command]])
      .filter(_ != classOf[Command])
  }

  implicit def factory[A <: AggregateRoot[_] : ClassTag]() = new AggregateRootFactory[A] {
    override def props: Props = Props(implicitly[ClassTag[A]].runtimeClass)
  }

  implicit def idResolution[A <: AggregateRoot[_] : ClassTag]() = new AggregateIdResolution[A] {
    override def resolve(command: Command): String = {
      val aggregateTypename = implicitly[ClassTag[A]].runtimeClass.getSimpleName

      val commandClass = command.getClass
      val annotation = commandClass.getDeclaredAnnotation[AggregateIdField](classOf[AggregateIdField])
      annotation match {
        case null => throw new IllegalArgumentException(s"Not provided AggregateIdField annotation for command '${commandClass.getSimpleName}'. Please set annotation or specify an id resolver for type '$aggregateTypename'")
        case _ =>
          val fieldname = annotation.fieldname()
          val method = commandClass.getMethod(fieldname)
          method.invoke(command).asInstanceOf[String]
      }
    }

    private def getTypeTag[T: ru.TypeTag](obj: T) = ru.typeTag[T]
    private def junk[T: ru.TypeTag](v: T) = {
      val t = implicitly[ru.TypeTag[T]]
      t.tpe.typeArgs.map {
        a=>
        val m = ru.runtimeMirror(getClass.getClassLoader)
        m.runtimeClass(a.typeSymbol.asClass)
      }
    }
  }
}