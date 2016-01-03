package juju.domain.resolvers

import juju.domain.AggregateRoot.{AggregateHandlersResolution, AggregateIdResolution}
import juju.domain._
import juju.domain.resolvers.ByConventionsSpec._
import juju.messages.Activate
import juju.sample.AveragePersonWeightSaga.{PublishAverageWeight, PublishWakeUp, PublishRequested}
import juju.sample.PersonAggregate._
import juju.sample.PriorityAggregate.{CreatePriority, PriorityCreated}
import juju.sample._
import juju.testkit.LocalDomainSpec

import scala.reflect.ClassTag

class ByConventionsSpec extends LocalDomainSpec("ByConvention") {

  it should "retrieves commands supported by the aggregate" in {
    val supportedCommands = ByConventions.aggregateHandlersResolution[PersonAggregate]().resolve()
    supportedCommands should contain allOf(classOf[CreatePerson], classOf[ChangeWeight], classOf[ChangeHeight])
  }

  it should "returns empty if aggregate doesn't provide handle specific methods" in {
    val supportedCommands = ByConventions.aggregateHandlersResolution[ColorAggregate]().resolve()
    supportedCommands should be('empty)
  }

  it should "creates Aggregate using byConvention factory" in {
    val props = ByConventions.aggregateFactory[PersonAggregate]().props
    val fakeRef = system.actorOf(props)

    fakeRef ! CreatePerson("giangi")
    expectMsg(timeout.duration, PersonCreated("giangi"))

    fakeRef ! ChangeWeight("giangi", 80)
    expectMsg(WeightChanged("giangi", 80))
  }

  it should "extract aggregate id from command using byConvention resolver" in {
    val aggregateId = ByConventions.aggregateIdResolution[AggregateWithValidHandleAnnotation]().resolve(ChangeWeight("giangi", 80))
    aggregateId shouldBe "giangi"
  }

  it should "throw error if aggregate id from command byConvention resolver doesn't find AggregateIdField annotation" in {
    the[IllegalArgumentException] thrownBy {
      ByConventions.aggregateIdResolution[AggregateWithNoHandleAnnotation]().resolve(ChangeWeight("giangi", 1))
    } should have message "Not provided AggregateIdField annotation for command 'ChangeWeight'. Please set annotation or specify an id resolver for type 'AggregateWithNoHandleAnnotation'"
  }

  it should "throw error if aggregate id from command byConvention resolver doesn't exist" in {
    the[NoSuchMethodError] thrownBy {
      ByConventions.aggregateIdResolution[AggregateWithInvalidHandleAnnotation]().resolve(ChangeWeight("giangi", 1))
    } should have message "Aggregate 'AggregateWithInvalidHandleAnnotation' specifies not existing Aggregate id field 'notexistingfield' for command 'ChangeWeight'"
  }

  //TODO: add tests for aggregateid field convention name 'AggregateName + Id'
  //TODO: add tests for aggregateid field convention name 'Id'

  it should "be able to use convention from office" in {
    import juju.infrastructure.local.LocalOffice

    implicit def idResolution[A <: AggregateRoot[_] : ClassTag]: AggregateIdResolution[A] = ByConventions.aggregateIdResolution[A]()
    implicit def factory[A <: AggregateRoot[_] : ClassTag]: AggregateRootFactory[A] = ByConventions.aggregateFactory[A]()
    implicit def handlersResolution[A <: AggregateRoot[_] : ClassTag]: AggregateHandlersResolution[A] = ByConventions.aggregateHandlersResolution[A]()

    system.eventStream.subscribe(this.testActor, classOf[PersonCreated])
    system.eventStream.subscribe(this.testActor, classOf[WeightChanged])

    val officeRef = LocalOffice.localOfficeFactory[PersonAggregate](tenant).getOrCreate

    officeRef ! CreatePerson("giangi")
    expectMsg(timeout.duration, PersonCreated("giangi"))

    officeRef ! ChangeWeight("giangi", 80)
    expectMsg(timeout.duration, WeightChanged("giangi", 80))
  }

  it should "be able to override convention from office with more specific type" in {
    import juju.infrastructure.local.LocalOffice

    implicit def idResolution[A <: AggregateRoot[_] : ClassTag]: AggregateIdResolution[A] = ByConventions.aggregateIdResolution[A]()
    implicit def factory[A <: AggregateRoot[_] : ClassTag]: AggregateRootFactory[A] = ByConventions.aggregateFactory[A]()
    implicit def handlersResolution[A <: AggregateRoot[_] : ClassTag]: AggregateHandlersResolution[A] = ByConventions.aggregateHandlersResolution[A]()

    implicit val priorityAggregateIdResolution = PriorityAggregate.idResolution
    implicit val priorityAggregateHandlersResolution = PriorityAggregate.handlersResolution

    system.eventStream.subscribe(this.testActor, classOf[PriorityCreated])

    val officeRef = LocalOffice.localOfficeFactory[PriorityAggregate](tenant).getOrCreate
    officeRef ! CreatePriority("giangi")

    expectMsg(timeout.duration, PriorityCreated("giangi"))
  }

  it should "retrieves events supported by the saga" in {
    val supportedEvents = ByConventions.sagaHandlersResolution[AveragePersonWeightSaga]().resolve()
    supportedEvents should contain allOf(classOf[WeightChanged], classOf[PublishRequested])
  }

  it should "returns empty if saga doesn't provide apply specific methods" in {
    val supportedEvents = ByConventions.sagaHandlersResolution[PriorityActivitiesSaga]().resolve()
    supportedEvents should be('empty)
  }

  it should "retrieves wakeup supported by the saga" in {
    val supportedWakeups = ByConventions.sagaHandlersResolution[AveragePersonWeightSaga]().wakeUpBy()
    supportedWakeups should contain(classOf[PublishWakeUp])
  }

  it should "returns empty if saga doesn't provide wakeup specific methods" in {
    val supportedWakeups = ByConventions.sagaHandlersResolution[PriorityActivitiesSaga]().wakeUpBy()
    supportedWakeups should be('empty)
  }

  it should "retrieves activate supported by the saga" in {
    val supportedActivate = ByConventions.sagaHandlersResolution[SagaWithActivation]().activateBy()
    supportedActivate should not be None
    val clazz = supportedActivate.get.getName
    clazz should be(classOf[SagaActivate].getName)
  }

  it should "returns None if saga doesn't provide activate annotation" in {
    val supportedActivates = ByConventions.sagaHandlersResolution[SagaWithNoActivationAnnotation]().activateBy()
    supportedActivates should be(None)
  }

  it should "creates Saga using byConvention factory" in {
    val props = ByConventions.sagaFactory[AveragePersonWeightSaga]().props("fake", this.testActor)
    val fakeRef = system.actorOf(props)

    fakeRef ! WeightChanged("giangi", 80)

    fakeRef ! PublishWakeUp()
    expectMsg(PublishAverageWeight(80))
  }

  it should "extract correlation id from command using byConvention resolver" in {
    val correlationId = ByConventions.correlationIdResolution[SagaWithValidApplyAnnotation]().resolve(WeightChanged("giangi", 80))
    correlationId should not be None
    correlationId.get shouldBe "giangi"
  }

  it should "throw error if correlation id from command byConvention resolver doesn't find CorrelationIdField annotation" in {
    the[IllegalArgumentException] thrownBy {
      ByConventions.correlationIdResolution[SagaWithNoApplyAnnotation]().resolve(WeightChanged("giangi", 80))
    } should have message "Not provided CorrelationIdField annotation for event 'WeightChanged'. Please set annotation or specify a correlation id resolver for type 'SagaWithNoApplyAnnotation'"
  }

  it should "throw error if correlation id from command byConvention resolver doesn't exist" in {
    the[NoSuchMethodError] thrownBy {
      ByConventions.correlationIdResolution[SagaWithInvalidApplyAnnotation]().resolve(WeightChanged("giangi", 80))
    } should have message "Saga 'SagaWithInvalidApplyAnnotation' specifies not existing Correlation id field 'notexistingfield' for event 'WeightChanged'"
  }

  //TODO: add tests for correlationId field convention name 'Correlation + Id'
  //TODO: add tests for correlationId field convention name 'Id'

}
object ByConventionsSpec {
  class AggregateWithValidHandleAnnotation extends AggregateRoot[EmptyState] {
    override val factory: AggregateStateFactory = {
      case _ => throw new IllegalArgumentException("Cannot create the aggregate")
    }

    @AggregateIdField(fieldname = "name")
    def handle(command: ChangeWeight): Unit = command match {
      case _ =>
    }
  }

  class AggregateWithInvalidHandleAnnotation extends AggregateRoot[EmptyState] {
    override val factory: AggregateStateFactory = {
      case _ => throw new IllegalArgumentException("Cannot create the aggregate")
    }

    @AggregateIdField(fieldname = "notexistingfield")
    def handle(command: ChangeWeight): Unit = command match {
      case _ =>
    }
  }

  class AggregateWithNoHandleAnnotation extends AggregateRoot[EmptyState] {
    override val factory: AggregateStateFactory = {
      case _ => throw new IllegalArgumentException("Cannot create the aggregate")
    }

    def handle(command: ChangeWeight): Unit = command match {
      case _ =>
    }
  }

  case class SagaActivate(override val correlationId: String) extends Activate
  @ActivatedBy(message = classOf[SagaActivate]) class SagaWithActivation extends Saga {}
  class SagaWithNoActivationAnnotation extends Saga {}

  class SagaWithValidApplyAnnotation extends Saga {
    @CorrelationIdField(fieldname = "name")def apply(event: WeightChanged): Unit = ???
  }
  class SagaWithInvalidApplyAnnotation extends Saga {
    @CorrelationIdField(fieldname = "notexistingfield")def apply(event: WeightChanged): Unit = ???
  }
  class SagaWithNoApplyAnnotation extends Saga {
    def apply(event: WeightChanged): Unit = ???
  }
}
