package akka.persistence.kafka

import juju.messages.DomainEvent

class DomainEventTopicMapper extends EventTopicMapper {

  def getBoundedContext(e: DomainEvent) = "finance"

  def getEventName(e: DomainEvent) = e.getClass.getSimpleName

  def topicsFor(event: Event): scala.collection.immutable.Seq[String] = {
    event.data match {
      case e : DomainEvent => {
        val bc = getBoundedContext(e)
        val eventname = getEventName(e)
        val topicname = if (bc != null && bc.trim != "") s"${bc}_$eventname" else eventname
          List("events", topicname)
      }
      case _ => {
        List("infrastructure")
      }
    }
  }
}
