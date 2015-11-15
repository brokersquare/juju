package juju.infrastructure

import juju.infrastructure.local.LocalNode
import juju.testkit.infrastructure.SagaRouterSpec

class LocalSagaRouterSpec extends SagaRouterSpec("Local") with LocalNode {}

