package juju.infrastructure

import juju.infrastructure.local.LocalNode
import juju.testkit.infrastructure.EventBusSpec



class LocalEventBusSpec extends EventBusSpec("Local") with LocalNode {
}
