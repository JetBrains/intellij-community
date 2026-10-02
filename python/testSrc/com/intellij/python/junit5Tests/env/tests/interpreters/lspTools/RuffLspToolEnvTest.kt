// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.components.service
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.ruff.RuffConfiguration
import com.intellij.python.ruff.RuffPyTool
import com.intellij.python.ruff.server.RuffLspIntegrationProvider
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

/**
 * End-to-end test of the Ruff LSP tool support against the real `ruff` executable.
 *
 * Verifies two editor-driven Ruff features. Reformatting goes through the LSP server.
 * Import optimization runs the Ruff executable with the `I` and `F401` rules.
 * The built-in PyCharm formatter and import optimizer do not normalize quotes or sort the names in one `from` import.
 * So a passing result can only come from Ruff.
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
    toolInstalled = toolInstalled,
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
    // The code uses the names, so the F401 rule keeps the import.
    codeInsightFixture.configureByText("imports.py", "from a import c, b\n\nprint(b, c)\n")
    codeInsightFixture.performEditorAction("OptimizeImports")
    codeInsightFixture.checkResult("from a import b, c\n\nprint(b, c)\n")
  }

  @AfterEach
  fun tearDownTool(): Unit = timeoutRunBlocking {
    tearDownLspTool(project, RuffLspIntegrationProvider::class.java)
  }

  companion object {
    private val toolInstalled = AtomicBoolean(false)
    private val tempPathFixture = tempPathFixture()
    private val projectFixture = projectFixture(openAfterCreation = true)
    internal val project by projectFixture
    private val moduleFixture = projectFixture.pyModuleFixture(tempPathFixture, addPathToSourceRoot = true)
    internal val module by moduleFixture
    internal val venv by pySdkFixture().pyVenvFixture(
      where = tempPathFixture,
      addToSdkTable = true,
      moduleFixture = moduleFixture,
    )
    internal val codeInsightFixture by codeInsightFixture(projectFixture, tempPathFixture)
  }
}
