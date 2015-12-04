package juju.infrastructure

import akka.actor.ActorRef
import juju.domain.Saga

import scala.language.existentials
import scala.reflect.ClassTag

object SagaRouter {
  def router[S <: Saga : SagaRouterFactory]: ActorRef = {
    implicitly[SagaRouterFactory[S]].getOrCreate
  }

  case class SagaIsUp(clazz: Class[_ <: Saga], actorRef: ActorRef, tenant: String, correlationId: String)

  def nameWithTenant(tenant: String, name: String): String = {
    if (tenant == null || tenant.trim == "") {
      name
    } else {
      s"${tenant}_$name"
    }
  }

  def nameWithTenant(tenant: String, message: Class[_]): String = nameWithTenant(tenant, message.getSimpleName)
}

abstract class SagaRouterFactory[S <: Saga : ClassTag] {
  val tenant: String
  def getOrCreate: ActorRef
  def routerName = SagaRouter.nameWithTenant(tenant, implicitly[ClassTag[S]].runtimeClass.getSimpleName)
}

