// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.black

import com.intellij.python.black.BlackPyTool
import com.intellij.python.black.configuration.BlackFormatterConfiguration
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.pytools.PyToolsState
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

/**
 * Verifies the one-way migration of Black's pre-[PyToolsState] settings. The old
 * [BlackFormatterConfiguration] fields must be imported into a [PyToolsState.ToolEntry] and then
 * cleared, so that re-running the migration (the reset-then-reopen path) imports nothing and cannot
 * resurrect the old values. The `black.formatter.support.enabled` registry key defaults to `true`.
 */
@PyEnvTestCase
internal class BlackMigrationTest {
  private val projectFixture = projectFixture()

  @Suppress("DEPRECATION")
  @Test
  fun `migrate imports legacy Black settings, then clears them so re-running imports nothing`() {
    val project = projectFixture.get()
    val cfg = BlackFormatterConfiguration.getBlackConfiguration(project).apply {
      enabledOnReformat = true
      executionMode = BlackFormatterConfiguration.ExecutionMode.BINARY
      pathToExecutable = "/usr/local/bin/black"
    }

    assertEquals(
      PyToolsState.ToolEntry(
        enabled = true,
        discoveryMode = ExecutableDiscoveryMode.PATH,
        customToolBinaryPath = Path("/usr/local/bin/black"),
      ),
      BlackPyTool.getInstance().migrateLegacyState(project),
    )

    // old settings are wiped, so a second migration (e.g. after the file was emptied on reset) imports nothing
    assertEquals(false, cfg.enabledOnReformat)
    assertEquals(BlackFormatterConfiguration.ExecutionMode.PACKAGE, cfg.executionMode)
    assertNull(cfg.pathToExecutable)
    assertEquals(PyToolsState.ToolEntry(), BlackPyTool.getInstance().migrateLegacyState(project))
  }
}
