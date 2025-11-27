package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.coroutine.perClassSupervisorScope
import com.intellij.ide.starter.coroutine.perTestSupervisorScope
import com.intellij.ide.starter.coroutine.testSuiteSupervisorScope
import com.intellij.ide.starter.utils.catchAll
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.*
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import kotlin.time.Duration.Companion.seconds

/**
 * The listener do the following:
 * * Cancels [perTestSupervisorScope], [perClassSupervisorScope] and [testSuiteSupervisorScope]
 * * Drops all subscriptions to StarterBus
 * * Resets configuration storage
 *
 */
open class TestCleanupListener : TestExecutionListener {
  init {
    // Shutdown hook is needed to make sure we will surely cancel the scope on builds cancellation on TC.
    val shutdownHookThread = Thread(Runnable {
      val reason = "Shutdown is in progress: either SIGTERM or SIGKILL is caught"
      logOutput("Canceling supervisor scopes: $reason")
      perTestSupervisorScope.cancel(CancellationException(reason))
      perClassSupervisorScope.cancel(CancellationException(reason))
      testSuiteSupervisorScope.cancel(CancellationException(reason))
    }, "test-scopes-shutdown-hook")
    try {
      Runtime.getRuntime().addShutdownHook(shutdownHookThread)
    }
    catch (e: IllegalStateException) {
      logError("Shutting down test scopes: Shutdown hook cannot be added because: ${e.message}")
    }
  }

  private fun cancelSupervisorScope(scope: CoroutineScope, message: String) {
    logOutput("Canceling children of '${scope.coroutineContext[CoroutineName]?.name}': $message")
    val timeout = 3.seconds

    @Suppress("SSBasedInspection")
    runBlocking {
      catchAll {
        scope.coroutineContext.cancelChildren(CancellationException((message)))
        // Wait with timeout - don't hang indefinitely
        val joinResult = withTimeoutOrNull(timeout) {
          scope.coroutineContext.job.children.forEach { it.join() }
        }

        if (joinResult == null) {
          logError("Some child coroutines of ${scope.coroutineContext[CoroutineName]?.name} didn't complete in $timeout cancellation timeout. Proceeding anyway. " +
                   "Children: ${scope.coroutineContext.job.children.joinToString(",")}")
        }
      }
    }
  }

  override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
    val testIdentifierName = testIdentifier.displayName
    if (testIdentifier.isContainer) {
      cancelSupervisorScope(perClassSupervisorScope, "Test class `$testIdentifierName` execution is finished")
    }

    if (testIdentifier.isTest) {
      cancelSupervisorScope(perTestSupervisorScope, "Test `$testIdentifierName` execution is finished")
      ConfigurationStorage.instance().resetToDefault()
    }

    super.executionFinished(testIdentifier, testExecutionResult)
  }

  override fun testPlanExecutionFinished(testPlan: TestPlan) {
    cancelSupervisorScope(testSuiteSupervisorScope, "Test plan execution is finished")
    super.testPlanExecutionFinished(testPlan)
  }
}