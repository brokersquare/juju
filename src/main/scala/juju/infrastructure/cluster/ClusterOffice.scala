package juju.infrastructure.cluster

import akka.actor._
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.{AggregateRootFactory, AggregateRoot}
import juju.infrastructure.OfficeFactory

import scala.reflect.ClassTag

object ClusterOffice {
  implicit def clusterOfficeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](implicit system : ActorSystem) = {
    new OfficeFactory[A] {
      override def getOrCreate: ActorRef = ???
    }
  }
}

class ClusterOffice[A <: AggregateRoot[_]](implicit ct: ClassTag[A], idResolver : AggregateIdResolution[A], aggregateFactory : AggregateRootFactory[A])
  extends Actor with ActorLogging {

  override def receive: Receive = ???
}