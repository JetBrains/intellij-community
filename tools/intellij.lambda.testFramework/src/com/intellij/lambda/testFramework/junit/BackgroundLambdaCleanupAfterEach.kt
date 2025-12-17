package com.intellij.lambda.testFramework.junit

import com.intellij.lambda.testFramework.starter.IdeInstance
import com.intellij.remoteDev.tests.impl.utils.runLoggedBlocking
import com.intellij.testFramework.recordErrorsLoggedInTheCurrentThreadAndReportThemAsFailures
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Ensures `BackgroundRunWithLambda.cleanUp()` is executed automatically after each test.
 *
 * - Tolerates absence of a started IDE (no-op).
 * - Logs failures but does not fail the test to avoid masking the original result.
 */
class BackgroundLambdaCleanupAfterEach : AfterEachCallback {
  override fun afterEach(context: ExtensionContext) {
    runLoggedBlocking("Cleaning up Lambda test session(s) after test: ${context.displayName}") {
      recordErrorsLoggedInTheCurrentThreadAndReportThemAsFailures {
        IdeInstance.cleanup()
      }
    }
  }
}
