package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.coroutineScopesCancellationTimeout
import com.intellij.ide.starter.coroutine.CommonScope.testSuiteSupervisorScope
import com.intellij.ide.starter.coroutine.CommonScope.perClassSupervisorScope
import com.intellij.ide.starter.coroutine.CommonScope.perTestSupervisorScope
import com.intellij.ide.starter.utils.catchAll
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.*
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan

/**
 * The listener do the following:
 * * Cancels [perTestSupervisorScope], [perClassSupervisorScope] and [testSuiteSupervisorScope]
 * * Drops all subscriptions to StarterBus
 * * Resets configuration storage
 *
 */
open class TestCleanupListener : TestExecutionListener {
  fun cancelPerTestSupervisorScope(testIdentifier: TestIdentifier) {
    if (!testIdentifier.isTest) return

    cancelSupervisorScope(perTestSupervisorScope, "Test `${testIdentifier.displayName}` execution is finished")
    ConfigurationStorage.instance().resetToDefault()
  }

  override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
    val testIdentifierName = testIdentifier.displayName
    if (testIdentifier.isContainer) {
      cancelSupervisorScope(perClassSupervisorScope, "Test class `$testIdentifierName` execution is finished")
    }

    cancelPerTestSupervisorScope(testIdentifier)
  }

  override fun testPlanExecutionFinished(testPlan: TestPlan) {
    cancelSupervisorScope(testSuiteSupervisorScope, "Test plan execution is finished")
  }
}

fun cancelSupervisorScope(scope: CoroutineScope, message: String) {
  logOutput("Canceling children of '${scope.coroutineContext[CoroutineName]?.name}': $message")
  val timeout = ConfigurationStorage.coroutineScopesCancellationTimeout

  @Suppress("SSBasedInspection")
  runBlocking {
    catchAll {
      scope.coroutineContext.cancelChildren(CancellationException((message)))
      // Wait with timeout - don't hang indefinitely
      val joinResult = withTimeoutOrNull(timeout) {
        scope.coroutineContext.job.children.forEach { it.cancelAndJoin() }
      }

      if (joinResult == null) {
        logError("Some child coroutines of ${scope.coroutineContext[CoroutineName]?.name} didn't complete in $timeout cancellation timeout. Proceeding anyway. " +
                 "Children: ${scope.coroutineContext.job.children.joinToString(",")}")
      }
    }
  }
}