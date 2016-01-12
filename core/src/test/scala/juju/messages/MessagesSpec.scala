package juju.messages

import org.scalatest.{FunSuiteLike, Matchers}

case class DoSomething(foo: String) extends Command
case class SomethingDone(foo: String) extends DomainEvent

class MessageSpec extends FunSuiteLike with Matchers {

  test("Created Command has a timestamp with default value") {
    DoSomething("fake").timestamp.before(new java.util.Date)
  }

  test("Created Event has a timestamp with default value") {
    SomethingDone("fake").timestamp.before(new java.util.Date)
  }
}