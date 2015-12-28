package juju.domain.resolvers

import juju.domain.{Handle, AggregateRoot}
import juju.domain.AggregateRoot.AggregateHandlersResolution
import juju.messages.Command

import scala.reflect.ClassTag

object ByConventions {
  implicit def handlersResolution[A <: AggregateRoot[_] : ClassTag]() = new AggregateHandlersResolution[A] {
    override def resolve(): Seq[Class[_ <: Command]] = implicitly[ClassTag[A]].runtimeClass.getDeclaredMethods
      .filter(_.getParameterTypes.length == 1)
      .filter(_.getName == classOf[Handle[_ <: Command]].getMethods.head.getName)
      .map(_.getParameterTypes.head.asInstanceOf[Class[_ <: Command]])
      .filter(_ != classOf[Command])
  }
}