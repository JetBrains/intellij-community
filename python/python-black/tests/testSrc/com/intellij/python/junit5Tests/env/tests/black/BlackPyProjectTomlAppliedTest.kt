// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.black

import com.intellij.openapi.application.runReadAction
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pytools.getState
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.python.black.BlackFormattingRequest
import com.intellij.python.black.BlackFormattingResponse
import com.intellij.python.black.BlackPyTool
import com.intellij.python.black.execute
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Exercises Black's reading of project-level configuration from `pyproject.toml`.
 * Migrated from JUnit4 `BlackPyProjectTomlAppliedTest`.
 */
@PyEnvTestCase
internal class BlackPyProjectTomlAppliedTest {
  companion object {
    val tempPathFixture = tempPathFixture()
    val projectFixture = projectFixture(tempPathFixture, openAfterCreation = true)
    val moduleFixture = projectFixture.moduleFixture(tempPathFixture, addPathToSourceRoot = true)
    val sdkFixture = pySdkFixture().pyEnvSdkFixture(moduleFixture)

    @JvmStatic
    @BeforeAll
    fun enableBlack() {
      with (BlackPyTool.getInstance().getState(projectFixture.get())) {
        enabled = true
        discoveryMode = ExecutableDiscoveryMode.INTERPRETER
      }
    }
  }

  private val project get() = projectFixture.get()
  private val tempDir: Path get() = tempPathFixture.get()

  @Test
  fun testSkipMagicTrailingCommaApplied(testInfo: TestInfo) {
    tempDir.resolve(PY_PROJECT_TOML).writeText("""
      [tool.black]
      skip-magic-trailing-comma = true
    """.trimIndent())

    val fileName = "${testInfo.testMethod.get().name}.py"
    val psiFile = createPsiFileOnDisk(project, tempDir, fileName, """
      custom_formatting = [
          0,  1,  2,
          3,  4,  5,
          6,  7,  8,
      ]

    """.trimIndent())
    reformatPsiFile(project, psiFile)
    val actual = runReadAction { psiFile.text }
    assertEquals("""
      custom_formatting = [0, 1, 2, 3, 4, 5, 6, 7, 8]

      """.trimIndent(), actual)
  }

  @Test
  fun testForceExcludedFileIgnored() {
    tempDir.resolve(PY_PROJECT_TOML).writeText("""
      [tool.black]
      force-exclude = '.*\/test\.py'
    """.trimIndent())

    val text = "print('ignore me!')"
    // File name is fixed to `test.py` to match the `force-exclude` regex above.
    val psiFile = createPsiFileOnDisk(project, tempDir, "test.py", text)

    val request = BlackFormattingRequest.File(psiFile.virtualFile, text)
    val response = timeoutRunBlocking {
      BlackPyTool.getInstance().execute(project, request)
    }
    assertInstanceOf(BlackFormattingResponse.Ignored::class.java, response)
  }
}
