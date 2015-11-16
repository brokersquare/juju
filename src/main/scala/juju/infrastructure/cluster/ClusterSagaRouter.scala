package juju.infrastructure.cluster

import akka.actor._
import juju.domain.Saga.{SagaCorrelationIdResolution, SagaHandlersResolution}
import juju.domain._
import juju.infrastructure.SagaRouterFactory

import scala.reflect.ClassTag

object ClusterSagaRouter {
  implicit def clusterSagaRouterFactory[S <: Saga : ClassTag :SagaHandlersResolution : SagaCorrelationIdResolution : SagaFactory](implicit system : ActorSystem) = new SagaRouterFactory[S] {
    override def getOrCreate: ActorRef = ???
  }
}

class ClusterSagaRouter[S <: Saga](implicit ct: ClassTag[S], handlersResolution: SagaHandlersResolution[S], idResolver : SagaCorrelationIdResolution[S], sagaFactory : SagaFactory[S])
  extends Actor with ActorLogging {

  override def receive: Receive = ???
}