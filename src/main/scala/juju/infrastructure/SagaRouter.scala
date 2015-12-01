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
  val tenant: String
  def getOrCreate: ActorRef
  def routerName =
    if (tenant == null || tenant == "") {
      implicitly[ClassTag[S]].runtimeClass.getSimpleName
    }
  else {
      s"${tenant}_" + implicitly[ClassTag[S]].runtimeClass.getSimpleName
    }
}

