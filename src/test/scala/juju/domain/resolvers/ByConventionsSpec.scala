package juju.domain.resolvers

import juju.sample.{PersonAggregate, ColorAggregate}
import juju.sample.PersonAggregate.ChangeWeight
import juju.testkit.LocalDomainSpec

class ByConventionsSpec extends LocalDomainSpec("ByConvention") {

  it should "retrieve aggregate supported commands" in {
    val supportedCommands = ByConventions.handlersResolution[PersonAggregate]().resolve()
    supportedCommands should have length 1
    supportedCommands.head shouldBe classOf[ChangeWeight]
  }

  it should "returns empty if aggregate doesn't provide handle specific methods" in {
    val supportedCommands = ByConventions.handlersResolution[ColorAggregate]().resolve()
    supportedCommands should be ('empty)
  }
}