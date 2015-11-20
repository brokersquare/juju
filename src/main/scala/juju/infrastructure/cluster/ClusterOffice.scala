package juju.infrastructure.cluster

import akka.actor._
import akka.contrib.pattern.{ClusterSharding, ShardRegion}
import juju.domain.AggregateRoot.AggregateIdResolution
import juju.domain.{AggregateRoot, AggregateRootFactory}
import juju.infrastructure.OfficeFactory
import juju.messages.Command

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object ClusterOffice {
  implicit def clusterOfficeFactory[A <: AggregateRoot[_] : AggregateIdResolution : AggregateRootFactory : ClassTag](implicit system : ActorSystem) = {
    new OfficeFactory[A] {
      override def getOrCreate: ActorRef = {
        region.getOrElse {
          startSharding()
          region.get
        }
      }

      private def region: Option[ActorRef] = {
        Try(ClusterSharding(system).shardRegion(officeName)) match {
          case Success(ref) => Some(ref)
          case Failure(ex) if ex.isInstanceOf[IllegalArgumentException] => None
        }
        /*
        try {
          Some(ClusterSharding(system).shardRegion(officeName))
        } catch {
          case ex: IllegalArgumentException => None
        }*/
      }

      private def startSharding(): Unit = {
        val props = implicitly[AggregateRootFactory[A]].props
        val resolution = implicitly[AggregateIdResolution[A]]
        val clazz = implicitly[ClassTag[A]].runtimeClass.asInstanceOf[Class[A]]
        val className = clazz.getSimpleName

        val idExtractor: ShardRegion.IdExtractor = {
          case cmd : Command => (resolution.resolve(cmd), cmd)
          case _ => ???
        }

        val shardResolver: ShardRegion.ShardResolver = {
          case cmd: Command => Integer.toHexString(resolution.resolve(cmd).hashCode).charAt(0).toString
          case _ => ???
        }
        ClusterSharding(system).start(className, Some(props), idExtractor, shardResolver)
      }
    }
  }
}