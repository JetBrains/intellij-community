// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.interpreters.lspTools

import com.intellij.openapi.components.service
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.python.ty.TyConfiguration
import com.intellij.python.ty.TyLspIntegrationProvider
import com.intellij.python.ty.TyPyTool
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
 * End-to-end test of the ty LSP tool support against the real `ty` type checker.
 *
 * Installs ty into the test venv, enables the tool, and verifies that a type error reported via
 * `textDocument/publishDiagnostics` is surfaced as an editor error highlight by the
 * [com.intellij.python.lsp.core.PyLspToolCustomization] diagnostics pipeline.
 */
@Subsystems.LspTools
@Layers.Functional
@TestApplication
@PyEnvTestCase
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class TyLspToolEnvTest {
  private suspend fun enableTyAndInstall() = pyProject.enableLspToolAndInstall(
    project = project,
    pyTool = TyPyTool.getInstance(),
    toolInstalled = toolInstalled,
  ) {
    project.service<TyConfiguration>().apply {
      inspections = true
    }
  }

  @Test
  fun `reports a type error diagnostic`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    enableTyAndInstall()
    val file = codeInsightFixture.configureByText("typecheck.py", "x: int = \"not an int\"\n")
    val errors = awaitLspErrorDiagnostics(project, file.virtualFile, TyLspIntegrationProvider::class.java)
    assertReportsIntAssignmentError(errors, "ty")
  }

  @AfterEach
  fun tearDownTool(): Unit = timeoutRunBlocking {
    tearDownLspTool(project, TyLspIntegrationProvider::class.java)
  }

  companion object {
    private val toolInstalled = AtomicBoolean(false)
    private val tempPathFixture = tempPathFixture()
    private val projectFixture = projectFixture(openAfterCreation = true)
    internal val project by projectFixture
    private val moduleFixture = projectFixture.pyModuleFixture(tempPathFixture, addPathToSourceRoot = true)
    internal val pyProject by moduleFixture.pyProjectFixture()
    internal val venv by pySdkFixture().pyVenvFixture(
      where = tempPathFixture,
      addToSdkTable = true,
      moduleFixture = moduleFixture,
    )
    internal val codeInsightFixture by codeInsightFixture(projectFixture, tempPathFixture)
  }
}
