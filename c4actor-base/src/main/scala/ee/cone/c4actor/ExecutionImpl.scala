
package ee.cone.c4actor

import java.util.concurrent._

class ExecutionImpl(
  toStart: List[Executable]
) extends Executable {
  private def sleep() = Thread.sleep(1000)
  def run(ctx: ExecutionContext): Unit = {
    println(s"tracking ${toStart.size} services")
    val serving = new CompletableFuture
    toStart.foreach(f ⇒ CompletableFuture.runAsync(new Runnable {
      def run(): Unit = f.run(ctx)
    },ctx.executors).exceptionally(new java.util.function.Function[Throwable, Void] {
      def apply(t: Throwable): Void = {
        serving.completeExceptionally(t)
        ().asInstanceOf[Void]
      }
    }))
    serving.get()
  }
}

class Main(f: ExecutionContext⇒Unit) {
  private def onShutdown(f: ()⇒Unit): Unit =
    Runtime.getRuntime.addShutdownHook(new Thread(){
      override def run(): Unit = f()
    })
  def main(args: Array[String]): Unit = try {
    Trace {
      val pool = Executors.newCachedThreadPool() //newWorkStealingPool
      onShutdown(()⇒{
        pool.shutdown()
        pool.awaitTermination(Long.MaxValue,TimeUnit.SECONDS)
      })
      f(new ExecutionContext(pool,onShutdown))
    }
  } finally System.exit(0)
}