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
      logOutput("Canceling suite supervisor scopes: $reason")
      val ce = CancellationException(reason)
      perTestSupervisorScope.cancel(ce)
      perClassSupervisorScope.cancel(ce)
      testSuiteSupervisorScope.cancel(ce)
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
   * Lifespan is limited to the duration of the test class. By the end of the test container, a whole coroutines tree will be canceled.
   * In a simple case a test container is the same as a class, in case when we have a base class and several classes extend it, the test container is over when all tests extending the base class are over.
   */
  val perClassSupervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("Test class's supervisor scope"))

  var scopeForProcesses: CoroutineScope = perClassSupervisorScope
    private set

  /*
    Processes started in ProcessExecutor and by various runWithDriver will be launched on per suite scope.
    It means that they will be stopped when the whole test suite is finished and not after each test container.
   */
  fun perSuiteScopeForIdeActivities() {
    scopeForProcesses = testSuiteSupervisorScope
  }

  fun shouldKillOutdatedProcessesBetweenContainers(): Boolean = scopeForProcesses == perClassSupervisorScope
}