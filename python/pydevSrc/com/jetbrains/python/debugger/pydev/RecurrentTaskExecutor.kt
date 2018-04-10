// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ConcurrencyUtil
import com.jetbrains.python.debugger.pydev.RecurrentTaskExecutor.Companion.FIXED_THREAD_POOL_SIZE
import com.jetbrains.python.debugger.pydev.RecurrentTaskExecutor.Companion.PERFORM_TASK_ATTEMPT_DELAY
import com.jetbrains.python.debugger.pydev.RecurrentTaskExecutor.RecurrentTask
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Provides the way to execute monotonous lengthy tasks performed by the
 * [recurrentTask]. The task may either succeed and the [T] result is returned
 * or it may fail and the [Exception] is thrown in this case.
 *
 * The tasks are tried to be executed using [FIXED_THREAD_POOL_SIZE] threads.
 * The new task execution is scheduled after the [RecurrentTaskExecutor]
 * receives new request(s) using [incrementRequests]. When this happens one of
 * the [executorService] threads calls [RecurrentTask.tryToPerform] of the
 * [recurrentTask]. If the call does not end in [PERFORM_TASK_ATTEMPT_DELAY]
 * period of time or fail within it then the next thread attempts to do the
 * same task.
 *
 * Finally when the next task has been successfully executed the number of
 * requests is decreased.
 *
 * **Note!** The single request could be successfully executed by several
 * threads what would decrement [activeRequests] so that it could become a
 * negative number.
 *
 * The workflow of using the [RecurrentTaskExecutor]:
 * - instantiate [RecurrentTaskExecutor];
 * - call [RecurrentTaskExecutor.incrementRequests] when the new request is
 *   required to be performed;
 * - handle the successfully executed request at
 *   [RecurrentTaskExecutor.Callback.onSuccess];
 * - call [RecurrentTaskExecutor.dispose] when `this` [RecurrentTaskExecutor]
 *   is no longer needed to shutdown the executor service and stop the task
 *   threads.
 */
class RecurrentTaskExecutor<out T>(threadsName: String,
                                   private val recurrentTask: RecurrentTask<T>,
                                   private val callback: Callback<T>) {
  private val executorService: ExecutorService

  /**
   * Guards [activeRequests].
   */
  private val lock = ReentrantLock()

  /**
   * Represents the number of the:
   * - increased on [incrementRequests];
   * - decreased on the successful task execution after which the
   *   [RecurrentTaskExecutor.Callback.onSuccess] callback method executed.
   *
   * Guarded by [lock].
   */
  private var activeRequests = 0

  /**
   * Occurs when [activeRequests] > 0.
   */
  private val notZeroRequests = lock.newCondition()

  private val timeCondition = lock.newCondition()

  /**
   * Guarded by [lock].
   */
  private var lastRequestAttemptTime = System.nanoTime()

  init {
    val threadFactory = ConcurrencyUtil.newNamedThreadFactory(threadsName)
    executorService = Executors.newFixedThreadPool(FIXED_THREAD_POOL_SIZE, threadFactory)
    repeat(FIXED_THREAD_POOL_SIZE, { executorService.submit(TaskRunnable()) })
  }

  fun incrementRequests() {
    lock.withLock {
      activeRequests++

      notZeroRequests.signalAll()
    }
  }

  fun dispose() {
    executorService.shutdownNow()
  }

  private inner class TaskRunnable : Runnable {
    override fun run() {
      while (true) {
        try {
          waitForRequests()
          tryToPerformTaskAndHandleSuccess()
        }
        catch (e: InterruptedException) {
          if (executorService.isShutdown) {
            // exit while loop
            return
          }
          else {
            // this case is not generally expected
            LOG.debug(e)
          }
        }
        catch (e: Exception) {
          LOG.debug(e)
        }
      }
    }
  }

  /**
   * Waits for [hasActiveRequests] to become `true`. Also the method does not
   * return until [PERFORM_TASK_ATTEMPT_DELAY] elapses since the last
   * [waitForRequests] exit.
   *
   * @throws InterruptedException if [dispose] has been called and the
   *         [executorService] has been shut down
   */
  @Throws(InterruptedException::class)
  private fun waitForRequests() {
    lock.withLock {
      do {
        while (!hasActiveRequests()) {
          notZeroRequests.await()
        }

        // we have process requests, let's check if we should start
        do {
          // check the latest time we tried to perform the task

          val currentTime = System.nanoTime()
          val timeToStart = lastRequestAttemptTime + PERFORM_TASK_ATTEMPT_DELAY

          if (timeToStart > currentTime) {
            val timeToSleep = timeToStart - currentTime

            timeCondition.awaitNanos(timeToSleep)
          }
          else {
            // it's time to run!
            break
          }
        }
        while (hasActiveRequests())
      }
      while (!hasActiveRequests()) // let's check requests again

      // finally we made through it
      lastRequestAttemptTime = System.nanoTime()
    }
  }

  /**
   * **Note!** Should be called within [lock].
   */
  private fun hasActiveRequests() = activeRequests > 0

  /**
   * Tries to perform the task using [RecurrentTask.tryToPerform]. On
   * successful execution it decrements the [activeRequests] number and
   * discharges the [lastRequestAttemptTime] for other worker threads to try
   * their luck if [hasActiveRequests] is still `true`.
   */
  @Throws(Exception::class, InterruptedException::class)
  private fun tryToPerformTaskAndHandleSuccess() {
    val requestResult = recurrentTask.tryToPerform()

    lock.withLock {
      activeRequests--

      // let the next tasks attempt to perform the task
      lastRequestAttemptTime = System.nanoTime() - PERFORM_TASK_ATTEMPT_DELAY
      timeCondition.signalAll()
    }

    callback.onSuccess(requestResult)
  }

  interface RecurrentTask<out T> {
    @Throws(Exception::class, InterruptedException::class)
    fun tryToPerform(): T
  }

  interface Callback<in T> {
    fun onSuccess(result: T)
  }

  companion object {
    private const val FIXED_THREAD_POOL_SIZE = 10

    private val LOG = Logger.getInstance(RecurrentTaskExecutor::class.java)

    private val PERFORM_TASK_ATTEMPT_DELAY = TimeUnit.MILLISECONDS.toNanos(500L)
  }
}