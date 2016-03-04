package akka.persistence.kafka

import java.io.File

import org.apache.commons.io.FileUtils
import org.scalatest._

trait KafkaCleanup extends BeforeAndAfterAll { this: Suite =>

  override def afterAll(): Unit = {
    FileUtils.deleteDirectory(new File("target/test"))
  }
}
