// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.runtime

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.*
import libraries.klogging.logger
import runtime.Cancellable
import runtime.CoroutineExceptionLogger
import runtime.Dispatcher
import runtime.TaskCancellable
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

private val log = logger<ApplicationDispatcher>()

internal class ApplicationDispatcher(disposable: Disposable, private val application: Application) : Dispatcher {
  private val executor = AppExecutorUtil.createBoundedScheduledExecutorService("Application Auxiliary Scheduler", 1)

  private val context = ApplicationCoroutineContext(application, executor)

  private val contextWithLog = context + CoroutineExceptionLogger.create(log)

  override val coroutineContext: CoroutineContext
    get() = contextWithLog

  init {
    Disposer.register(disposable, Disposable {
      executor.shutdown()
      try {
        executor.awaitTermination(5, TimeUnit.SECONDS)
      }
      catch (e: InterruptedException) {
        log.warn { "Executor was not properly terminated" }
      }
    })
  }

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
    val disposable = executor.scheduleWithFixedDelay(invoke, delay.toLong(), interval.toLong(), TimeUnit.MILLISECONDS)

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

  override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
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
