package com.intellij.ide.starter.coroutine

import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.cancellation.CancellationException

object CommonScope {
  init {
    // Shutdown hook is needed to make sure we will surely cancel the scope on test process abrupt exit
    val shutdownHookThread = Thread(Runnable {
      val reason = "Shutdown is in progress: either SIGTERM or SIGKILL is caught"
      logOutput("Canceling suite supervisor scope: $reason")
      testSuiteSupervisorScope.cancel(CancellationException(reason))
    }, "test-scopes-shutdown-hook")
    try {
      Runtime.getRuntime().addShutdownHook(shutdownHookThread)
    }
    catch (e: IllegalStateException) {
      logError("Shutting down suite supervisor scope: Shutdown hook cannot be added because: ${e.message}")
    }
  }

  /**
   * Lifespan is as long as the entire test suite run. When the test suite is finished, a whole coroutines tree will be canceled.
   * Unhandled exceptions in the tree of child coroutines will not affect any other coroutines (parent included).
   * Usually that is what you need.
   */
  val testSuiteSupervisorScope =
    CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("Test suite's supervisor scope"))

  /**

   * Lifespan is limited to duration each test. By the end of the test a whole coroutines tree will be canceled.
   * Unhandled exceptions in the tree of child coroutines will not affect any other coroutines (parent included).
   * Usually that is what you need.
   */
  val perTestSupervisorScope =
    CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("Test's supervisor scope"))

  /**
   * Lifespan is limited to the duration of the test class. By the end of the test class whole coroutines tree will be cancelled.
   */
  val perClassSupervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("Test class's supervisor scope"))

  /**
   * In case of unhandled exception in child coroutines, all the coroutines tree (parents and other branches) will be canceled.
   * In most scenarios you don't need that behavior.
   */
  val simpleScope = CoroutineScope(Job() + Dispatchers.IO + CoroutineName("Simple scope"))
}