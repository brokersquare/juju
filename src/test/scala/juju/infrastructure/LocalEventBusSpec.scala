package juju.infrastructure

import juju.testkit.infrastructure.EventBusSpec



class LocalEventBusSpec extends EventBusSpec("Local") with UsingLocalEventBus {
}
