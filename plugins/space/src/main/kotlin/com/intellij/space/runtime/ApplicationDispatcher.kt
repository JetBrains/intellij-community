package com.intellij.space.runtime

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ModalityState
import kotlinx.coroutines.*
import libraries.klogging.logger
import runtime.Cancellable
import runtime.CoroutineExceptionLogger
import runtime.Dispatcher
import runtime.TaskCancellable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

val log = logger<ApplicationDispatcher>()

class ApplicationDispatcher(private val application: Application) : Dispatcher {
  private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
    val thread = Thread(runnable, "Application Auxiliary Scheduler")

    thread.isDaemon = true
    thread
  }

  private val context = ApplicationCoroutineContext(application, executor)

  private val contextWithLog = context + CoroutineExceptionLogger.create(log)

  override val coroutineContext: CoroutineContext
    get() = contextWithLog

  override fun dispatch(fn: () -> Unit) {
    application.invokeLater(fn, ModalityState.any())
  }

  override fun dispatch(delay: Int, fn: () -> Unit): Cancellable {
    val invoke = java.lang.Runnable {
      application.invokeLater(fn, ModalityState.any())
    }
    val disposable = executor.schedule(invoke, delay.toLong(), TimeUnit.MILLISECONDS)

    return TaskCancellable(disposable)
  }

  override fun dispatchInterval(delay: Int, interval: Int, fn: () -> Unit): Cancellable {
    val invoke = java.lang.Runnable {
      application.invokeLater(fn, ModalityState.any())
    }
    val disposable = executor.scheduleAtFixedRate(invoke, delay.toLong(), interval.toLong(), TimeUnit.MILLISECONDS)

    return TaskCancellable(disposable)
  }
}

@UseExperimental(InternalCoroutinesApi::class)
private class ApplicationCoroutineContext(
  private val application: Application,
  private val executor: ScheduledExecutorService
) : CoroutineDispatcher(), Delay {

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    application.invokeLater(block, ModalityState.any())
  }

  override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
    val toResume = java.lang.Runnable {
      application.invokeLater({ with(continuation) { this@ApplicationCoroutineContext.resumeUndispatched(Unit) } }, ModalityState.any())
    }

    executor.schedule(toResume, timeMillis, TimeUnit.MILLISECONDS)
  }

  override fun invokeOnTimeout(timeMillis: Long, block: Runnable): DisposableHandle {
    val toResume = java.lang.Runnable {
      application.invokeLater({
                                block.run()
                              }, ModalityState.any())
    }

    return DisposableFutureHandle(executor.schedule(toResume, timeMillis, TimeUnit.MILLISECONDS))
  }
}

private class DisposableFutureHandle(private val future: Future<*>) : DisposableHandle {
  override fun dispose() {
    future.cancel(false)
  }

  override fun toString(): String = "DisposableFutureHandle[$future]"
}
