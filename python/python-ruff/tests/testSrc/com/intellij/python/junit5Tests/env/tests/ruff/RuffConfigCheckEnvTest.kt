// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.ruff

import com.intellij.openapi.module.Module
import com.intellij.python.ruff.codeinsight.RuffConfigError
import com.intellij.python.ruff.codeinsight.checkRuffConfig
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.ruff.RuffPyTool
import com.intellij.python.test.env.junit5.LspToolVersions
import com.intellij.python.test.env.junit5.installToolPackage
import com.intellij.python.test.env.junit5.pyVenvFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.sdk.ModuleOrProject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

/**
 * End-to-end test of [checkRuffConfig] against the real `ruff` executable, which
 * `RuffConfigExternalAnnotator` uses to highlight an error in a Ruff config file.
 *
 * Nothing here mocks Ruff's output. `ERROR_PATTERN` in `RuffConfigCheck.kt` must match what Ruff
 * really prints, and the pinned version ([LspToolVersions]) keeps that output stable.
 *
 * This test runs Ruff in a local venv only. It does not cover the WSL and Docker case, because the
 * env test framework gives no remote interpreter.
 */
@Subsystems.LspTools
@Layers.Functional
@TestApplication
@PyEnvTestCase
@Timeout(value = 10, unit = TimeUnit.MINUTES)
internal class RuffConfigCheckEnvTest {

  @Test
  fun `broken toml syntax reports the parse position`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    installRuff()
    // `line-length` has no value, so the TOML parser fails on line 2.
    val error = check(
      """
      [lint]
      line-length =
      """.trimIndent()
    )

    assertNotNull(error, "A broken TOML config must produce an error")
    assertEquals(2, error!!.line, "The error must point at the line that fails to parse")
    assertTrue(error.column >= 1, "The column must be 1-based, got ${error.column}")
    assertTrue(error.width >= 1, "The highlight must cover at least one character")
    assertTrue(error.message.isNotBlank(), "Ruff's own wording must reach the annotation")
  }

  @Test
  fun `unknown ruff option reports an error`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    installRuff()
    // Valid TOML, but `not-a-ruff-option` is not a Ruff setting.
    val error = check(
      """
      not-a-ruff-option = 1
      """.trimIndent()
    )

    assertNotNull(error, "An unknown Ruff option must produce an error")
    assertEquals(1, error!!.line, "The error must point at the unknown option")
    assertTrue(error.message.isNotBlank(), "Ruff's own wording must reach the annotation")
  }

  @Test
  fun `valid config reports no error`(): Unit = timeoutRunBlocking(timeout = 5.minutes) {
    installRuff()
    val error = check(
      """
      line-length = 100

      [lint]
      select = ["E", "F"]
      """.trimIndent()
    )

    assertNull(error, "A valid config must produce no error")
  }

  private suspend fun installRuff() {
    // The venv fixture puts the SDK on the module, and `installToolPackage` reads that SDK.
    venvFixture.get()
    if (ruffInstalled.compareAndSet(false, true)) {
      module.installToolPackage(LspToolVersions.requirement(RuffPyTool.getInstance()))
    }
  }

  private suspend fun check(configText: String): RuffConfigError? =
    checkRuffConfig(
      moduleOrProject = ModuleOrProject.ModuleAndProject(module),
      configFileName = "ruff.toml",
      configText = configText,
      workingDir = workingDir,
    ).orThrow()

  companion object {
    /** Guards the one-time Ruff install, so the three tests share one venv and one download. */
    private val ruffInstalled = AtomicBoolean(false)

    private val tempPathFixture = tempPathFixture()
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.pyModuleFixture(tempPathFixture, addPathToSourceRoot = true)
    private val venvFixture = pySdkFixture().pyVenvFixture(
      where = tempPathFixture,
      addToSdkTable = true,
      moduleFixture = moduleFixture,
    )

    // `@TestApplication` implies `@TestFixtures`, which initializes every field of type `TestFixture`.
    private val module: Module get() = moduleFixture.get()
    private val workingDir: Path get() = tempPathFixture.get()
  }
}
