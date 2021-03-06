
package ee.cone.c4actor

import java.util.concurrent.{ExecutorService, Executors, ThreadFactory, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal

object VMExecution {
  def onShutdown(hint: String, f: () ⇒ Unit): ()⇒Unit = {
    val thread = new Thread(new ShutdownRunnable(hint,f))
    Runtime.getRuntime.addShutdownHook(thread)
    () ⇒ try {
      Runtime.getRuntime.removeShutdownHook(thread)
    } catch {
      case e: IllegalStateException ⇒ ()
    }
  }
  def newThreadPool(prefix: String): ExecutorService = {
    val defaultThreadFactory = Executors.defaultThreadFactory()
    val threadFactory = new RThreadFactory(defaultThreadFactory,prefix)
    Executors.newCachedThreadPool(threadFactory) //newWorkStealingPool
  }
}

class ShutdownRunnable(hint: String, f: () ⇒ Unit) extends Runnable with LazyLogging {
  def run(): Unit = {
    logger.debug(s"hook-in $hint")
    f()
    logger.debug(s"hook-out $hint")
  }
}

class RThreadFactory(inner: ThreadFactory, prefix: String) extends ThreadFactory {
  def newThread(runnable: Runnable): Thread = {
    val thread = inner.newThread(runnable)
    thread.setName(s"$prefix${thread.getName}")
    thread
  }
}

class VMExecution(getToStart: ()⇒List[Executable])(
  threadPool: ExecutorService = VMExecution.newThreadPool("tx-")
)(
  val executionContext: ExecutionContext = ExecutionContext.fromExecutor(threadPool)
) extends Execution with LazyLogging {
  def run(): Unit = {
    val toStart = getToStart()
    logger.debug(s"tracking ${toStart.size} services")
    toStart.foreach(f ⇒ future(()).map(_⇒f.run()))
  }
  def onShutdown(hint: String, f: () ⇒ Unit): ()⇒Unit =
    VMExecution.onShutdown(hint,f)
  def complete(): Unit = { // exit from pooled thread will block itself
    logger.info("exiting")
    System.exit(0)
  }
  def future[T](value: T): FatalFuture[T] =
    new VMFatalFuture(Future.successful(value))(executionContext)
  def skippingFuture[T](value: T): FatalFuture[T] =
    new VMFatalSkippingFuture[T](Future.successful(value))(executionContext)
  def newThreadPool(prefix: String): ExecutorService =
    VMExecution.newThreadPool(prefix)
}

class VMFatalSkippingFuture[T](inner: Future[T])(implicit executionContext: ExecutionContext) extends FatalFuture[T] with LazyLogging {
  private def canSkip[T](future: Future[T]) = future match {
    case a: AtomicReference[_] ⇒ a.get() match {
      case s: Seq[_] ⇒ s.nonEmpty
      case u ⇒ logger.warn(s"no skip rule for inner ${u.getClass.getName}"); false
    }
    case u ⇒ logger.warn(s"no skip rule for outer ${u.getClass.getName}"); false
  }
  def map(body: T ⇒ T): FatalFuture[T] = {
    lazy val nextFuture: Future[T] = inner.map(from ⇒ try {
      if(canSkip(nextFuture)) from else body(from)
    } catch {
      case e: Throwable ⇒
        logger.error("fatal",e)
        System.exit(1)
        throw e
    })
    new VMFatalSkippingFuture(nextFuture)
  }
  def value: Option[Try[T]] = inner.value
}

class VMFatalFuture[T](val inner: Future[T])(implicit executionContext: ExecutionContext) extends FatalFuture[T] with LazyLogging {
  def map(body: T ⇒ T): FatalFuture[T] =
    new VMFatalFuture(inner.map(from ⇒ try body(from) catch {
      case e: Throwable ⇒
        logger.error("fatal",e)
        System.exit(1)
        throw e
    }))
  def value: Option[Try[T]] = inner.value
}

abstract class BaseServerMain(app: ExecutableApp){
  def main(args: Array[String]): Unit = try {
    Trace {
      app.execution.run()
      //println("main is about to sleep")
      Thread.sleep(Long.MaxValue) //ctx.serving.get()
    }
  } finally System.exit(0)
}

object ServerMain extends BaseServerMain(
  Option(Class.forName((new EnvConfigImpl).get("C4STATE_TOPIC_PREFIX"))).get
    .newInstance().asInstanceOf[ExecutableApp]
)

class EnvConfigImpl extends Config {
  def get(key: String): String =
    Option(System.getenv(key)).getOrElse(throw new Exception(s"Need ENV: $key"))
}

object CatchNonFatalImpl extends CatchNonFatal {
  def apply[T](aTry: ⇒T)(aCatch: Throwable⇒T): T =
    try { Trace{ aTry } } catch { case NonFatal(e) ⇒ aCatch(e) }
}