// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.pytools

import com.intellij.python.pytools.PyToolsState
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.python.pytools.lsp.PyLspToolConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

/**
 * Regression for the migration resurrection bug: legacy settings used to be re-read on every
 * [PyToolsState.getEntry], so resetting a migrated tool back to its defaults (which drops its
 * stored entry) would resurrect the old values on the next read.
 *
 * The fix makes migration one-way — [PyLspToolConfiguration.migrateToPyToolState] clears the old
 * settings as it imports them — so re-running the migration (e.g. after the storage file was
 * emptied on reset) imports nothing.
 */
internal class PyLspToolConfigurationMigrationTest {
  private class FakeConfig : PyLspToolConfiguration<FakeConfig>()

  @Suppress("DEPRECATION")
  @Test
  fun `migrateToToolEntry imports legacy settings, then clears them so re-running imports nothing`() {
    val cfg = FakeConfig().apply {
      enabled = true
      executableDiscoveryMode = ExecutableDiscoveryMode.PATH
      pathToExecutable = "/usr/local/bin/ruff"
    }

    assertEquals(
      PyToolsState.ToolEntry(
        enabled = true,
        discoveryMode = ExecutableDiscoveryMode.PATH,
        customToolBinaryPath = Path("/usr/local/bin/ruff"),
      ),
      cfg.migrateToPyToolState(),
    )

    // old settings are wiped, so a second migration (the reset-then-reopen path) yields nothing to import
    assertEquals(false, cfg.enabled)
    assertEquals(ExecutableDiscoveryMode.INTERPRETER, cfg.executableDiscoveryMode)
    assertEquals("", cfg.pathToExecutable)
    assertEquals(PyToolsState.ToolEntry(), cfg.migrateToPyToolState())
  }
}
