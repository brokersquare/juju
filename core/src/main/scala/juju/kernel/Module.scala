package juju.kernel

import akka.actor.{Actor, Props}

import scala.reflect.ClassTag

trait Module {
  actor: Actor =>
  def appname: String
  def role: String
}

abstract class ModulePropsFactory[A <: Module : ClassTag] {
  def props(moduleName: String, role :String) : Props = Props(implicitly[ClassTag[A]].runtimeClass, moduleName, role)
}
