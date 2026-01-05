package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.coroutine.testSuiteSupervisorScope
import com.intellij.lambda.testFramework.starter.IdeInstance.ide
import com.intellij.lambda.testFramework.starter.IdeInstance.isStarted
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.testFramework.recordErrorsLoggedInTheCurrentThreadAndReportThemAsFailures
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.*

/**
 * Ensures `BackgroundRunWithLambda.cleanUp()` is executed automatically after each test.
 *
 * - Tolerates absence of a started IDE (no-op).
 * - Logs failures but does not fail the test to avoid masking the original result.
 */
class BackgroundLambdaCleanupAfterEach : BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {
  override fun beforeAll(context: ExtensionContext) {
    runLifecycleCallback("Before all", context.requiredTestClass.name) {
      ide.beforeAll(context.requiredTestClass.name)
    }
  }

  override fun beforeEach(context: ExtensionContext) {
    val contextName = context.requiredTestClass.name + "." + context.requiredTestMethod.name + " " + context.displayName
    runLifecycleCallback("Before each", contextName) {
      ide.beforeEach(contextName)
    }
  }

  override fun afterEach(context: ExtensionContext) {
    val contextName = context.requiredTestClass.name + "." + context.requiredTestMethod.name + " " + context.displayName
    runLifecycleCallback("After each", contextName) {
      ide.afterEach(contextName)
    }
  }

  override fun afterAll(context: ExtensionContext) {
    runLifecycleCallback("After all", context.requiredTestClass.name) {
      ide.afterAll(context.requiredTestClass.name)
    }
  }


  private inline fun runLifecycleCallback(
    callbackName: String,
    contextName: String,
    crossinline action: suspend IdeWithLambda.() -> Unit,
  ): Unit = synchronized(this) {
    if (!isStarted()) {
      thisLogger().warn("IDE wasn't started yet. Skipping $callbackName for $contextName")
      return@synchronized
    }
    recordErrorsLoggedInTheCurrentThreadAndReportThemAsFailures {
      runCatching {
        @Suppress("RAW_RUN_BLOCKING")
        runBlocking(testSuiteSupervisorScope.coroutineContext) {
          ide.action()
        }
      }.onFailure { thisLogger().error("Problems in $callbackName: ${it.message}", it, contextName) }
    }
  }
}
