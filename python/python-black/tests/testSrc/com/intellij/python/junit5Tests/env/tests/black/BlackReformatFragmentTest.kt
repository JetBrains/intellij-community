// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.black

import com.intellij.openapi.application.runReadAction
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.pytools.getState
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.python.black.BlackPyTool
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path

/**
 * Fragment formatting tests for Black. Mirrors the JUnit4 `BlackReformatFragmentTest`.
 */
@PyEnvTestCase
internal class BlackReformatFragmentTest {
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
  fun testReformatFragment(testInfo: TestInfo) = runFragmentTest(
    testInfo = testInfo,
    startOffset = 0,
    endOffset = 60,
    input = """
      to_format = [
          0,  1,  2,
          3,  4,  5,
          6,  7,  8,
      ]

      leave_untouched = [
          0,  1,  2,
          3,  4,  5,
          6,  7,  8,
      ]
    """.trimIndent(),
    expected = """
      to_format = [
          0,
          1,
          2,
          3,
          4,
          5,
          6,
          7,
          8,
      ]

      leave_untouched = [
          0,  1,  2,
          3,  4,  5,
          6,  7,  8,
      ]

     """.trimIndent(),
  )

  @Test
  fun testReformatFragmentWithIndentation(testInfo: TestInfo) = runFragmentTest(
    testInfo = testInfo,
    startOffset = 37,
    endOffset = 119,
    input = """
    class Calc:
        def abc(self, a, b):
            if a>10 :
                if b!=0 :
                    print('a > 10 and b != 0')
            else:
                print("something else")
    """.trimIndent(),
    expected = """
    class Calc:
        def abc(self, a, b):
            if a > 10:
                if b != 0:
                    print("a > 10 and b != 0")
            else:
                print("something else")

     """.trimIndent(),
  )

  private fun runFragmentTest(
    testInfo: TestInfo,
    startOffset: Int,
    endOffset: Int,
    input: String,
    expected: String,
  ) {
    val fileName = "${testInfo.testMethod.get().name}.py"
    val psiFile = createPsiFileOnDisk(project, tempDir, fileName, input)
    reformatPsiFileRange(project, psiFile, startOffset, endOffset)
    val actual = runReadAction { psiFile.text }
    assertEquals(expected, actual, "Reformatted text differs for $fileName")
  }
}
