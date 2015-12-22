package juju.infrastructure

import akka.actor.ActorRef
import juju.domain.Saga

import scala.language.existentials
import scala.reflect.ClassTag

object SagaRouter {
  def router[S <: Saga : SagaRouterFactory](tenant: String = ""): ActorRef = {
    implicitly[SagaRouterFactory[S]].getOrCreate
  }

  case class SagaIsUp(clazz: Class[_ <: Saga], actorRef: ActorRef, tenant: String, correlationId: String)

  def nameWithTenant(tenant: String, name: String): String = {
    tenant match {
      case t if t == null || t.trim == "" => name
      case _ => s"${tenant}_$name"
    }
  }

  def nameWithTenant(tenant: String, message: Class[_]): String = nameWithTenant(tenant, message.getSimpleName)
}

abstract class SagaRouterFactory[S <: Saga : ClassTag] {
  val tenant: String
  def getOrCreate: ActorRef
  val className = implicitly[ClassTag[S]].runtimeClass.getSimpleName

  def routerName() : String = {
    SagaRouter.nameWithTenant(tenant, className)
  }
}

