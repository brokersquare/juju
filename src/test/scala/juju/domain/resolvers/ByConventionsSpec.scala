package juju.domain.resolvers

import juju.domain.AggregateRoot.{AggregateHandlersResolution, AggregateIdResolution}
import juju.domain.{AggregateRoot, AggregateRootFactory}
import juju.messages.Command
import juju.sample.PersonAggregate.{ChangeWeight, WeightChanged}
import juju.sample.PriorityAggregate.{PriorityCreated, CreatePriority}
import juju.sample.{ColorAggregate, PersonAggregate, PriorityAggregate}
import juju.testkit.LocalDomainSpec

import scala.reflect.ClassTag

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

  it should "throw error if aggregate id from command byConvention resolver doesn't find AggregateIdField annotation" in {
    the [IllegalArgumentException] thrownBy {
      ByConventions.idResolution[PersonAggregate]().resolve(CommandWithNoAnnotation("giangi"))
    } should have message "Not provided AggregateIdField annotation for command 'CommandWithNoAnnotation'. Please set annotation or specify an id resolver for type 'PersonAggregate'"
  }

  it should "throw error if aggregate id from command byConvention resolver doesn't exist" in {
    the [NoSuchMethodError] thrownBy {
      ByConventions.idResolution[PersonAggregate]().resolve(CommandWithInvalidAnnotation("giangi"))
    } should have message "Command 'CommandWithInvalidAnnotation' have no Aggregate id field 'notexistingfield'"
  }

  it should "be able to use convention from office" in {
    import juju.infrastructure.local.LocalOffice

    implicit def idResolution[A <: AggregateRoot[_] : ClassTag] : AggregateIdResolution[A] = ByConventions.idResolution[A]()
    implicit def factory[A <: AggregateRoot[_] : ClassTag] : AggregateRootFactory[A] = ByConventions.factory[A]()
    implicit def handlersResolution[A <: AggregateRoot[_] : ClassTag] : AggregateHandlersResolution[A] = ByConventions.handlersResolution[A]()

    system.eventStream.subscribe(this.testActor, classOf[WeightChanged])

    val officeRef = LocalOffice.localOfficeFactory[PersonAggregate](tenant).getOrCreate
    officeRef ! ChangeWeight("giangi", 80)

    expectMsg(timeout.duration, WeightChanged("giangi", 80))
  }

  it should "be able to override convention from office with more specific type" in {
    import juju.infrastructure.local.LocalOffice

    implicit def idResolution[A <: AggregateRoot[_] : ClassTag] : AggregateIdResolution[A] = ByConventions.idResolution[A]()
    implicit def factory[A <: AggregateRoot[_] : ClassTag] : AggregateRootFactory[A] = ByConventions.factory[A]()
    implicit def handlersResolution[A <: AggregateRoot[_] : ClassTag] : AggregateHandlersResolution[A] = ByConventions.handlersResolution[A]()

    implicit val priorityAggregateIdResolution = PriorityAggregate.idResolution
    implicit val priorityAggregateHandlersResolution = PriorityAggregate.handlersResolution

    system.eventStream.subscribe(this.testActor, classOf[PriorityCreated])

    val officeRef = LocalOffice.localOfficeFactory[PriorityAggregate](tenant).getOrCreate
    officeRef ! CreatePriority("giangi")

    expectMsg(timeout.duration, PriorityCreated("giangi"))
  }

  case class CommandWithNoAnnotation(dummy: String) extends Command
  @AggregateIdField(fieldname = "notexistingfield") case class CommandWithInvalidAnnotation(dummy: String) extends Command
}
