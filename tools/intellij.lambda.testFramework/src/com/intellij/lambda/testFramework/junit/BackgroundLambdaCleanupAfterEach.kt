package com.intellij.lambda.testFramework.junit

import com.intellij.lambda.testFramework.starter.IdeInstance
import com.intellij.tools.ide.util.common.starterLogger
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
    starterLogger<BackgroundLambdaCleanupAfterEach>().info("Cleaning up Lambda test session(s) after test: ${context.displayName}")

    IdeInstance.cleanup()
  }
}
