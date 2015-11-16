package juju.infrastructure.local

import juju.testkit.LocalDomainSpec
import juju.testkit.infrastructure.SagaRouterSpec

class LocalSagaRouterSpec extends LocalDomainSpec("LocalSagaRouter") with SagaRouterSpec {}
