package juju.domain.resolvers

import juju.messages.Command
import juju.sample.PersonAggregate.{ChangeWeight, WeightChanged}
import juju.sample.{ColorAggregate, PersonAggregate}
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

  it should "create Aggregate using byConvention factory" in {
    val props = ByConventions.factory[PersonAggregate]().props
    val fakeRef = system.actorOf(props)
    fakeRef ! ChangeWeight("giangi", 80)
    expectMsg(WeightChanged("giangi", 80))
  }

  it should "extract aggregate id from command using byConvention resolver" in {
    val aggregateId = ByConventions.idResolution[PersonAggregate]().resolve(ChangeWeight("giangi", 80))
    aggregateId shouldBe "giangi"
  }

  it should "throw error if aggregate id from command byConvention resolver doesn't exist" in {
    /*an [IllegalArgumentException] shouldBe
      thrownBy(ByConventions.idResolution[PersonAggregate]().resolve(CommandWithNoAnnotation("giangi"))) should have message ""
  */
    the [IllegalArgumentException] thrownBy {
      ByConventions.idResolution[PersonAggregate]().resolve(CommandWithNoAnnotation("giangi"))
    } should have message "Not provided AggregateIdField annotation for command 'CommandWithNoAnnotation'. Please set annotation or specify an id resolver for type 'PersonAggregate'"
  }
/*
  it should "throw error if aggregate id from command byConvention resolver doesn't find AggregateIdField annotation" in {
    assert(false, "not yet implemented")
  }

  it should "take first annotation if aggregate id from command byConvention resolver returns more than one AggregateIdField annotations" in {
    assert(false, "not yet implemented")
  }


  it should "be able to use convention from office" in {
    //    import ByConventions._
    //val officeRef = LocalOffice.localOfficeFactory[PersonAggregate](tenant).getOrCreate
    //officeRef ! ChangeWeight("giangi", 80)

    //expectMsg(timeout.duration, WeightChanged("giangi", 80))
    assert(false, "not yet implemented")
  }

  it should "be able to override convention from office with more specific type" in {
    assert(false, "not yet implemented")
  }*/

  case class CommandWithNoAnnotation(dummy: String) extends Command
  @AggregateIdField(fieldname = "notexistingfield") case class CommandWithInvalidAnnotation(dummy: String) extends Command
}