package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.coroutine.perTestSupervisorScope
import com.intellij.ide.starter.utils.catchAll
import com.intellij.lambda.testFramework.starter.IdeInstance
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.common.cleanApplicationState
import com.intellij.tools.ide.util.common.starterLogger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.time.Duration.Companion.seconds

/**
 * Ensures `BackgroundRunWithLambda.cleanUp()` is executed automatically after each test.
 *
 * - Tolerates absence of a started IDE (no-op).
 * - Logs failures but does not fail the test to avoid masking the original result.
 */
class BackgroundLambdaCleanupAfterEach : AfterEachCallback {
  override fun afterEach(context: ExtensionContext) {
    starterLogger<BackgroundLambdaCleanupAfterEach>().info("Cleaning up Lambda test session(s) after test: ${context.displayName}")

    invokeIdeApplicationCleanup()
    IdeInstance.cleanup()
  }

  private fun invokeIdeApplicationCleanup() {
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking(perTestSupervisorScope.coroutineContext) {
      withTimeout(5.seconds) {
        catchAll("Invoking test application cleanup") {
          if (!IdeInstance.isStarted() || !IdeInstance.runContext.frontendContext.calculateVmOptions().hasUnitTestMode()) return@catchAll

          IdeInstance.ide.apply {
            runInFrontend("IDE test application cleanup") {
              ApplicationManager.getApplication().cleanApplicationState()
            }
            runInBackend("IDE test application cleanup") {
              ApplicationManager.getApplication().cleanApplicationState()
            }
          }
        }
      }
    }
  }
}
