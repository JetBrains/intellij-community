package com.intellij.ide.starter.testContextCache

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.testContextCache.utils.currentTestContextDirName
import com.intellij.tools.ide.util.common.logOutput

/**
 * Configuration for a project context that [TestContextCache] prepares once and reuses.
 *
 * Reuse the same [CachedProjectConfiguration] instance when the prepared context should be reused.
 * Creating another configuration instance creates a separate cache entry and does not reuse the original prepared context.
 *
 * [setup] runs once, when that context is first created. It prepares the config, the plugins, the VM options and the
 * project files. It must not start an IDE. Every test of the context shares its `system` directory, so the first test
 * builds the indexes, and a warm-up in [setup] only moves that work.
 */
open class CachedProjectConfiguration(
  val testCase: TestCase<*>,
  val setup: IDETestContext.() -> IDETestContext = { this },
) {
  /**
   * Creates a separate configuration for the same [testCase] whose [setup] runs this configuration setup first
   * and then [additionalSetup].
   *
   * The returned instance is a separate cache entry.
   */
  fun withAdditionalSetup(additionalSetup: IDETestContext.() -> IDETestContext): CachedProjectConfiguration {
    val baseSetup = setup
    return CachedProjectConfiguration(
      testCase = testCase,
      setup = {
        additionalSetup(baseSetup(this))
      },
    )
  }

  /**
   * Creates a context of the current test over the prepared project of this configuration.
   *
   * For a test that starts and stops an IDE of its own. The project files go back to the prepared state first, so the
   * test starts from the state that [setup] left, whatever the test before it did. The context has its own `config` and
   * `plugins`, and the one shared `system` directory of the cached context.
   *
   * The other way to reuse a prepared project is `@ReusedRun`, where one IDE serves a whole test class.
   */
  fun createTestContext(): IDETestContext {
    val cachedTestContext = TestContextCache.getOrCreate(this)
    if (cachedTestContext.restorePreparedProjectFiles()) {
      cachedTestContext.requestProjectFilesVfsRefresh()
    }
    val currentTestContext = cachedTestContext.createCurrentTestContext(currentTestContextDirName())
    logOutput(
      "The current test runs on the context at ${currentTestContext.paths.testHome}, " +
      "over the project prepared at ${cachedTestContext.testContext.paths.testHome}"
    )
    return currentTestContext
  }
}
