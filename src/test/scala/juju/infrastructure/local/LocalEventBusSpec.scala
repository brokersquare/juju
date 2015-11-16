package juju.infrastructure.local

import juju.testkit.LocalDomainSpec
import juju.testkit.infrastructure.EventBusSpec


class LocalEventBusSpec extends LocalDomainSpec("LocalEventBus") with EventBusSpec {
}