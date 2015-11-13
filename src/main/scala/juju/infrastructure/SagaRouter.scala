package juju.infrastructure

import akka.actor.ActorRef
import juju.domain.Saga

import scala.reflect.ClassTag

object SagaRouter {
  def router[S <: Saga : SagaRouterFactory]: ActorRef = {
    implicitly[SagaRouterFactory[S]].getOrCreate
  }
}

abstract class SagaRouterFactory[S <: Saga : ClassTag] {
  def getOrCreate: ActorRef
  def routerName = implicitly[ClassTag[S]].runtimeClass.getSimpleName
}

