package ee.cone.c4actor

import java.io.ByteArrayInputStream
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets.UTF_8

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.LoggerFactory

import scala.annotation.tailrec

trait BasicLoggingApp extends ToStartApp {
  def catchNonFatal: CatchNonFatal
  def config: Config
  //
  private lazy val logbackIncludePath = Paths.get(config.get("C4LOGBACK_XML"))
  private lazy val logbackConfigurator =
    new LoggerConfigurator(logbackIncludePath, catchNonFatal, 5000)
  private lazy val logbackTest = new LoggerTest
  override def toStart: List[Executable] =
      Option(System.getenv("C4LOGBACK_TEST")).toList.map(_⇒logbackTest) :::
      logbackConfigurator ::
      super.toStart
}

class LoggerTest extends Executable with LazyLogging {
  def run(): Unit = iteration(0L)
  @tailrec private def iteration(v: Long): Unit = {
    Thread.sleep(1000)
    logger.warn(s"logger test $v")
    logger.debug(s"logger test $v")
    iteration(v+1L)
  }
}

class LoggerConfigurator(path: Path, catchNonFatal: CatchNonFatal, scanPeriod: Long) extends Executable {
  def run(): Unit = iteration("")
  @tailrec private def iteration(wasContent: String): Unit = {
    val content =
      s"""
      <configuration>
        <statusListener class="ch.qos.logback.core.status.NopStatusListener" />
        ${if(Files.exists(path)) new String(Files.readAllBytes(path), UTF_8) else ""}
        <appender name="CON" class="ch.qos.logback.core.ConsoleAppender">
          <encoder><pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern></encoder>
        </appender>
        <appender name="ASYNСCON" class="ch.qos.logback.classic.AsyncAppender">
          <discardingThreshold>0</discardingThreshold>
          <queueSize>1000000</queueSize>
          <appender-ref ref="CON" />
        </appender>
        <root level="INFO">
          <appender-ref ref="ASYNСCON" />
        </root>
        <shutdownHook/>
      </configuration>
      """
    if(wasContent != content) reconfigure(content)
    Thread.sleep(scanPeriod)
    iteration(content)
  }
  def reconfigure(content: String): Unit = catchNonFatal{
    println("logback reconfigure 2 started")
    val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val configurator = new JoranConfigurator()
    configurator.setContext(context)
    context.reset()
    configurator.doConfigure(new ByteArrayInputStream(content.getBytes(UTF_8)))
    println("logback reconfigure 2 ok")
  }{
    e ⇒ e.printStackTrace()
  }
}
