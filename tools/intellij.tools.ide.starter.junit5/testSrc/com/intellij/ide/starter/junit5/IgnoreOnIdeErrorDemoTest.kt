// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.junit5.config.KillOutdatedProcessesAfterEach
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.starter.bus.EventsBus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/**
 * Demo for [IgnoreOnIdeError] / [IgnoreOnIdeErrorExtension].
 *
 * Each test runs a real IDE, asks the IDE under test to log an error via the `%dropError`
 * playback command (see `DropErrorCommand`), then deliberately throws from the test body.
 * The extension inspects accumulated IDE errors and either downgrades the failure to ABORTED
 * (reported as `testIgnored` on TeamCity) or rethrows it unchanged.
 */
@ExtendWith(KillOutdatedProcessesAfterEach::class)
@ExtendWith(IgnoreOnIdeErrorExtension::class)
class IgnoreOnIdeErrorDemoTest {

  @AfterEach
  fun afterEach() {
    EventsBus.unsubscribeAll()
  }

  @Test
  @IgnoreOnIdeError(
      message = ".*Drop error from command.*",
      reason = "demo: matching filter",
    )
  fun matchingFilterDowngradesFailureToIgnored(testInfo: TestInfo, @TempDir projectDir: Path) {
    runIdeAndDropError(testInfo, projectDir)
    error("expected failure: simulating a real assertion that fails when the IDE errored")
  }

  /**
   * Negative demo. Disabled by default because enabling it produces a real CI failure —
   * the regex below intentionally does not match the IDE error emitted by `%dropError`,
   * so the extension does not downgrade the failure. Remove [Disabled] locally to verify.
   */
  @Test
  @Disabled(
    "Negative demo: enabling this test will produce a real CI failure to demonstrate " +
    "that a non-matching filter does not downgrade the failure."
  )
  @IgnoreOnIdeError(
    message = ".*never-matches-anything-zzz.*",
    reason = "demo: non-matching filter",
  )
  fun nonMatchingFilterStillFails(testInfo: TestInfo, @TempDir projectDir: Path) {
    runIdeAndDropError(testInfo, projectDir)
    error("expected failure: filter regex does not match the produced IDE error")
  }

  // An empty @TempDir is enough: the IDE will open it as a project, which lets
  // PerformancePluginInitProjectActivity fire and the %dropError script command run.
  // NoProject would never reach the project-scoped playback runner.
  private fun runIdeAndDropError(testInfo: TestInfo, projectDir: Path) {
    val context = Starter.newContext(
      testInfo.hyphenateWithClass(),
      TestCase(IdeInfo.IdeaUltimate, LocalProjectInfo(projectDir)).useRelease(),
    )
    context.runIDE(
      commands = CommandChain()
        .addCommand("%dropError demo error from IgnoreOnIdeErrorDemoTest")
        .exitApp(),
      runTimeout = 5.minutes,
    )
  }
}
