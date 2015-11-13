package juju.infrastructure

import akka.actor.ActorRef
import juju.domain.{AggregateRootFactory, AggregateRoot}
import juju.domain.AggregateRoot.AggregateIdResolution

import scala.reflect.ClassTag

object Office {
  def office[A <: AggregateRoot[_] : OfficeFactory]: ActorRef = implicitly[OfficeFactory[A]].getOrCreate
}

abstract class OfficeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag] {
  def getOrCreate: ActorRef
  def officeName = implicitly[ClassTag[A]].runtimeClass.getSimpleName
}