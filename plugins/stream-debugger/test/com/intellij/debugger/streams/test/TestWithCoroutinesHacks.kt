// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.test

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.resetThreadContext
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import java.util.concurrent.TimeoutException
import kotlin.time.Duration

/**
 * This code is a copy-paste from the com.jetbrains.rider.test.scriptingApi.runBlockingWithFlushing method. It is necessary to support coroutines in the old JUnit4 tests.
 * The problem with them - that it is impossible to run a test with runBlocking and have some coroutines switch to EDT context, because you will get deadlocked
 * It should be possible to run these tests from a different thread than EDT, but at the time - when I tried the `runInDispatchThread(): Boolean = false` spell it made the tests hang:
 * https://jetbrains.slack.com/archives/CMDBCUBGE/p1738151573576619
 */

val frameworkLogger = Logger.getInstance("#com.intellij.debugger.streams.testFramework")

@RequiresBlockingContext
fun <T> runBlockingWithFlushing(id: String, timeout: Duration, action: suspend CoroutineScope.() -> T): T {
  require(application.isUnitTestMode)
  ThreadingAssertions.assertEventDispatchThread()

  @Suppress("RAW_RUN_BLOCKING")
  return runBlocking(currentThreadContext()) {
    val task = async(Dispatchers.EDT, start = CoroutineStart.UNDISPATCHED) {
      withTimeoutAndDump("runBlockingWithFlushing $id", timeout, action)
    }

    resetThreadContext {
      pumpMessages { task.isCompleted }
    }
    task.await()
  }
}

private suspend fun <T> withTimeoutAndDump(name: String, timeout: Duration, action: suspend CoroutineScope.() -> T): T {
  return withTimeoutAndDump(name, timeout, timeout, action)
}

private suspend fun <T> withTimeoutAndDumpInternal(
  name: String,
  warnTimeout: Duration,
  exceptionTimeout: Duration,
  action: suspend CoroutineScope.() -> T
): T = coroutineScope {
  val scope = childScope(name)

  val timeoutJob = launch(start = CoroutineStart.UNDISPATCHED) {
    val timeout = if (warnTimeout < exceptionTimeout) {
      delay(warnTimeout)
      yield()
      frameworkLogger.warn("[WARNING] Activity: `$name` has not been completed in ${warnTimeout.inWholeMilliseconds} ms.")
      exceptionTimeout - warnTimeout
    } else {
      exceptionTimeout
    }

    delay(timeout)
    yield()
    val message = "Activity: `$name` has not been completed in ${timeout.inWholeMilliseconds} ms"
    frameworkLogger.error("$message\n")
    throw TimeoutException(message)
  }

  val result = scope.async(start = CoroutineStart.UNDISPATCHED) { action() }.await()

  timeoutJob.cancel()
  scope.cancel()

  result
}


private suspend fun <T> withTimeoutAndDump(name: String, warnTimeout: Duration, exceptionTimeout: Duration, action: suspend CoroutineScope.() -> T): T {
  frameworkLogger.info("$name started")
  val start = System.nanoTime()
  var exception: Throwable? = null
  return try {
    withTimeoutAndDumpInternal(name, warnTimeout, exceptionTimeout, action)
  }
  catch (e: Throwable) {
    exception = e
    throw e
  }
  finally {
    val message = "$name finished in ${(System.nanoTime() - start) / 1e6} ms"
    if (exception != null)
      frameworkLogger.warn("$message with exception", exception)
    else
      frameworkLogger.info(message)
  }
}

/**
 * Pumps UI thread within specified timeout until waitFunc() returns true
 * @return true if waitFunc() changes to true before the timeout is exceeded, false if timed out
 */
@RequiresBlockingContext
private fun pumpMessages(timeout: java.time.Duration = java.time.Duration.ofSeconds(-1), waitFunc: (() -> Boolean)): Boolean {
  fileLogger().assertTrue(application.isDispatchThread || !application.holdsReadLock()) {
    "Must not be called from read-action because it leads to possible deadlock"
  }

  val startTimeNs = System.nanoTime()
  val isDispatcherThread = ApplicationManager.getApplication().isDispatchThread
  // start 10ms sleeps after first 100ms of pumping - won't hurt that much, but will free up the cpu for other tasks
  val pumpingSleepStartTimeNs = startTimeNs + java.time.Duration.ofMillis(100).toNanos()
  val pumper =
    if (isDispatcherThread) { ->
      IdeEventQueue.getInstance().flushQueue()
    }
    else if (application.holdsReadLock()) { ->
      spinUntil(10) {
        waitFunc()
      }
    }
    else { ->
      UIUtil.pump()
    }

  while (true) {
    if (timeout.toNanos() > 0 && (System.nanoTime() - startTimeNs) > timeout.toNanos())
      return false
    if (waitFunc())
      return true
    pumper()
    if (System.nanoTime() > pumpingSleepStartTimeNs)
      Thread.sleep(10)
  }
}

private inline fun spinUntil(timeoutMs: Long, condition: () -> Boolean) {
  val start = System.nanoTime()
  var spins = 0L
  while (!condition()) {
    val timeoutNanos = timeoutMs * 1000L * 1000L
    if (System.nanoTime() - start > timeoutNanos)
      return
    if (spins++ < 100)
      Thread.yield()
    else
      Thread.sleep(spins / 100)
  }
}