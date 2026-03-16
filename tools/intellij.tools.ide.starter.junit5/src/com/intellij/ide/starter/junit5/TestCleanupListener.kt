package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.coroutineScopesCancellationTimeout
import com.intellij.ide.starter.coroutine.CommonScope.perClassSupervisorScope
import com.intellij.ide.starter.coroutine.CommonScope.perTestSupervisorScope
import com.intellij.ide.starter.coroutine.CommonScope.testSuiteSupervisorScope
import com.intellij.ide.starter.utils.catchAll
import com.intellij.tools.ide.util.common.logError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
  fun cancelPerTestSupervisorScope(testIdentifier: TestIdentifier, message: String? = null) {
    if (!testIdentifier.isTest) return

    cancelSupervisorScopeChildren(perTestSupervisorScope, message ?: "Test `${testIdentifier.displayName}` execution is finished")
    ConfigurationStorage.instance().resetToDefault()
  }

  override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
    val message = "Test `${testIdentifier.displayName}` execution is finished"
    cancelPerTestSupervisorScope(testIdentifier, message)
    if (testIdentifier.isContainer) {
      cancelSupervisorScopeChildren(perClassSupervisorScope, message)
    }
  }

  override fun testPlanExecutionFinished(testPlan: TestPlan) {
    val message = "Test plan execution is finished"
    cancelSupervisorScopeChildren(perTestSupervisorScope, message)
    cancelSupervisorScopeChildren(perClassSupervisorScope, message)
    cancelSupervisorScopeChildren(testSuiteSupervisorScope, message)
  }
}

private fun cancelSupervisorScopeChildren(scope: CoroutineScope, message: String) {
  val timeout = ConfigurationStorage.coroutineScopesCancellationTimeout

  @Suppress("SSBasedInspection")
  runBlocking {
    catchAll("Canceling children of '${scope.coroutineContext[CoroutineName]?.name}': $message") {
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