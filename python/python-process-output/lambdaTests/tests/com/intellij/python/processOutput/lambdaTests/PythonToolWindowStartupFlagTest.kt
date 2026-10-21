// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.processOutput.lambdaTests

import com.intellij.ide.starter.driver.reused.ReusedProject
import com.intellij.ide.starter.driver.reused.ReusedRun
import com.intellij.ide.starter.ide.IdeRunMode
import com.intellij.lambda.testFramework.junit.RunInMonolithAndSplitMode
import com.intellij.lambda.testFramework.junit.SetErrorIgnorerExtension
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.openapi.util.registry.Registry
import com.intellij.python.processOutput.lambdaTests.util.PythonProcessOutputLambdaStrategy
import com.intellij.python.processOutput.lambdaTests.util.pythonProcessOutputLambdaConfiguration
import com.intellij.testFramework.common.timeoutRunBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Duration.Companion.minutes

/**
 * PY-89999 ("RemDev: PPOTW is absent"): Python tool windows stayed hidden at startup on the split frontend.
 * They reach that decision through `SdkAwareToolWindowFactory.shouldBeAvailable()`, a passthrough of the
 * registry key asserted below. The fix is a descriptor change rather than code — the PyCharm Customization
 * plugin, which the split frontend bundles too, declares that key with `defaultValue="true"` instead of falling
 * back to the `false` in `intellij.python.community.common.xml`. The revert that makes this test red: drop that
 * declaration from `python/ide-customization/resources/META-INF/plugin.xml`.
 *
 * Split only. The monolith loads the same plugin and reads the same declaration (added for the monolith in
 * PY-88193), so a monolith invocation adds nothing to this one.
 *
 * This pins the flag, not the tool window. Locking the symptom itself means asking `ToolWindowManager` for
 * `PythonProcessOutput` via `waitForToolWindow`, which needs a project-bearing `CachedProjectConfiguration` this suite
 * does not have; until then, a change that re-gates the factory on an SDK would still pass here.
 *
 * The setup sets no override for this key, and that is load-bearing: a forced value would be read back
 * instead of the plugin-declared default, and the test would survive the revert above.
 */
@RunInMonolithAndSplitMode(IdeRunMode.SPLIT)
@ExtendWith(SetErrorIgnorerExtension::class)
@ReusedRun(sessionStrategy = PythonProcessOutputLambdaStrategy::class)
internal class PythonToolWindowStartupFlagTest {

  companion object {
    @ReusedProject
    private val project = pythonProcessOutputLambdaConfiguration
  }

  @Test
  fun `the python tool window startup-availability flag defaults to true on the split frontend`(ide: IdeWithLambda) =
    timeoutRunBlocking(TEST_TIMEOUT) {
      ide {
        val isAvailableAtStartupOnFrontend =
          runInFrontendGetResult("Read the tool-window startup-availability registry default") {
            Registry.`is`(STARTUP_AVAILABILITY_REGISTRY_KEY)
          } as Boolean

        assertThat(isAvailableAtStartupOnFrontend)
          .describedAs(
            "'%s' as loaded from the real frontend plugin set (no VM option or test-side override sets it)",
            STARTUP_AVAILABILITY_REGISTRY_KEY,
          )
          .isTrue()
      }
    }
}

private val TEST_TIMEOUT = 1.minutes

private const val STARTUP_AVAILABILITY_REGISTRY_KEY = "python.toolwindows.available.at.startup"
