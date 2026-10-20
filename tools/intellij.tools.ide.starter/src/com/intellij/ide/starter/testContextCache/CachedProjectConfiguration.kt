package com.intellij.ide.starter.testContextCache

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.TestCase

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
)
