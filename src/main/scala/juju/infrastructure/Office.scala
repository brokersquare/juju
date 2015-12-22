package juju.infrastructure

import akka.actor.ActorRef
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.{AggregateRoot, AggregateRootFactory}

import scala.reflect.ClassTag

object Office {
  def office[A <: AggregateRoot[_] : OfficeFactory](tenant: String = ""): ActorRef = implicitly[OfficeFactory[A]].getOrCreate
}

abstract class OfficeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag] {
  val tenant : String

  def getOrCreate: ActorRef

  def officeName: String =
    if (tenant == null || tenant == "") {
      implicitly[ClassTag[A]].runtimeClass.getSimpleName
    } else {
      s"${tenant}_" + implicitly[ClassTag[A]].runtimeClass.getSimpleName
    }
}