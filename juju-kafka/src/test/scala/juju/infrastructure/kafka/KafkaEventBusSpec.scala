package juju.infrastructure.kafka

import juju.testkit.KafkaDomainSpec
import juju.testkit.infrastructure.EventBusSpec


class KafkaEventBusSpec extends KafkaDomainSpec("KafkaEventBus", 2281) with EventBusSpec {
}
