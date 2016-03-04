package juju.infrastructure.kafka

import juju.testkit.LocalDomainSpec
import juju.testkit.infrastructure.EventBusSpec


class KafkaEventBusSpec extends LocalDomainSpec("KafkaEventBus") with EventBusSpec {
}
