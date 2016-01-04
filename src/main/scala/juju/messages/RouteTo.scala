package juju.messages

import juju.domain.AggregateRoot
import scala.language.existentials

case class RouteTo(senderClass: Class[_ <: AggregateRoot[_]], senderId: String, destinationClass: Class[_ <: AggregateRoot[_]], destinationId: String, message: Any) extends InfrastructureMessage