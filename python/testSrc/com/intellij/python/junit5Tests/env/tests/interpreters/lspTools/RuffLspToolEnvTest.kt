// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.components.service
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.ruff.RuffConfiguration
import com.intellij.python.ruff.RuffPyTool
import com.intellij.python.ruff.server.RuffLspIntegrationProvider
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/**
 * End-to-end test of the Ruff LSP tool support against the real `ruff` executable.
 *
 * Verifies the two editor-driven Ruff features routed through the LSP server:
 * reformatting and import optimization. These are the cases the built-in PyCharm formatter and
 * import optimizer intentionally do not handle (quote normalization, alphabetizing names in a
 * single `from` import), so a passing result can only come from Ruff.
 */
@Subsystems.LspTools
@Layers.Functional
@TestApplication
@PyEnvTestCase
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class RuffLspToolEnvTest {
  private suspend fun enableRuffAndInstall() = module.enableLspToolAndInstall(
    project = project,
    pyTool = RuffPyTool.getInstance(),
    toolInstalled = fixtures.toolInstalled,
  ) {
    project.service<RuffConfiguration>().apply {
      formatting = true
      sortImports = true
    }
  }

  @Test
  fun `reformat normalizes quotes via ruff`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    enableRuffAndInstall()
    val file = codeInsightFixture.configureByText("quotes.py", "'a'\n")
    awaitFileOpenedByLspServer(project, file.virtualFile, codeInsightFixture.testRootDisposable)
    codeInsightFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)
    codeInsightFixture.checkResult("\"a\"\n")
  }

  @Test
  fun `optimize imports sorts a single from-import via ruff`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    enableRuffAndInstall()
    val file = codeInsightFixture.configureByText("imports.py", "from a import c, b\n")
    awaitFileOpenedByLspServer(project, file.virtualFile, codeInsightFixture.testRootDisposable)
    codeInsightFixture.performEditorAction("OptimizeImports")
    codeInsightFixture.checkResult("from a import b, c\n")
  }

  @AfterEach
  fun tearDownTool(): Unit = timeoutRunBlocking {
    tearDownLspTool(project, RuffLspIntegrationProvider::class.java)
  }

  companion object {
    private val fixtures = PyLspToolEnvFixtures()
    internal val project by fixtures.projectFixture
    internal val module by fixtures.moduleFixture
    internal val venv by fixtures.venvFixture
    internal val codeInsightFixture by fixtures.codeInsightFixture
  }
}
