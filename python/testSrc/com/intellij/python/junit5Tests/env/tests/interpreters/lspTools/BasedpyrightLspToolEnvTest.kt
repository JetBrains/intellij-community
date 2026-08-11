// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.openapi.components.service
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.pyright.BasedpyrightConfiguration
import com.intellij.python.pyright.BasedpyrightPyTool
import com.intellij.python.pyright.PyrightLspIntegrationProvider
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
 * End-to-end test of the Pyright LSP tool support against the real `basedpyright` language server.
 *
 * [BasedpyrightPyTool] installs the `basedpyright` package and the binary is resolved as
 * `basedpyright-langserver`. The test verifies that a type error reported via
 * `textDocument/publishDiagnostics` is surfaced as an editor error highlight.
 */
@Subsystems.LspTools
@Layers.Functional
@TestApplication
@PyEnvTestCase
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class BasedpyrightLspToolEnvTest {
  private suspend fun enablePyrightAndInstall() = module.enableLspToolAndInstall(
    project = project,
    pyTool = BasedpyrightPyTool.getInstance(),
    toolInstalled = fixtures.toolInstalled,
  ) {
    project.service<BasedpyrightConfiguration>().apply {
      inspections = true
    }
  }

  @Test
  fun `reports a type error diagnostic`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    enablePyrightAndInstall()
    val file = codeInsightFixture.configureByText("typecheck.py", "x: int = \"not an int\"\n")
    val errors = awaitLspErrorDiagnostics(project, file.virtualFile, PyrightLspIntegrationProvider::class.java)
    assertReportsIntAssignmentError(errors, "basedpyright")
  }

  @AfterEach
  fun tearDownTool(): Unit = timeoutRunBlocking {
    tearDownLspTool(project, PyrightLspIntegrationProvider::class.java)
  }

  companion object {
    private val fixtures = PyLspToolEnvFixtures()
    internal val project by fixtures.projectFixture
    internal val module by fixtures.moduleFixture
    internal val venv by fixtures.venvFixture
    internal val codeInsightFixture by fixtures.codeInsightFixture
  }
}
