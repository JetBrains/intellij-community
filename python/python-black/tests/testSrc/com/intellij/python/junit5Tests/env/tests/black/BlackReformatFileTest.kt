// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.black

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.junit5Tests.framework.env.pySdkFixture
import com.intellij.python.pytools.configuration.ExecutableDiscoveryMode
import com.intellij.python.pytools.getState
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.python.black.BlackPyTool
import com.intellij.python.black.configuration.BlackFormatterConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import kotlin.io.path.writeText

@PyEnvTestCase
internal class BlackReformatFileTest {
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
  fun testLineLengthApplied(testInfo: TestInfo) = runBlackTest(
    testInfo = testInfo,
    extension = "py",
    cmdArguments = "-l 115",
    input = """if very_long_variable_name is not None and very_long_variable_name.field > 0 or very_long_variable_name.is_debug:
                  |    ...
                  |
                  |         """.trimMargin(),
    expected = """if very_long_variable_name is not None and very_long_variable_name.field > 0 or very_long_variable_name.is_debug:
        |    ...
        |""".trimMargin(),
  )

  @Test
  fun testSkipMagicTrailingCommaApplied(testInfo: TestInfo) = runBlackTest(
    testInfo = testInfo,
    extension = "py",
    cmdArguments = "--skip-magic-trailing-comma",
    input = """
      custom_formatting = [
          0,  1,  2,
          3,  4,  5,
          6,  7,  8,
      ]

    """.trimIndent(),
    expected = """
      custom_formatting = [0, 1, 2, 3, 4, 5, 6, 7, 8]

      """.trimIndent(),
  )

  @Test
  fun testStringNormalizationApplied(testInfo: TestInfo) = runBlackTest(
    testInfo = testInfo,
    extension = "py",
    cmdArguments = "--skip-string-normalization",
    input = """
      def test_function():
          '''this is my docstring'''
          pass
    """.trimIndent(),
    expected = """
      def test_function():
          '''this is my docstring'''
          pass

      """.trimIndent(),
  )

  @Test
  fun testNonPythonFileIgnored(testInfo: TestInfo) = runBlackTest(
    testInfo = testInfo,
    extension = "txt",
    cmdArguments = "",
    input = "print('abc')",
    expected = "print('abc')",
  )

  private fun runBlackTest(testInfo: TestInfo, extension: String, cmdArguments: String, input: String, expected: String) {
    BlackFormatterConfiguration.getBlackConfiguration(project).cmdArguments = cmdArguments

    // Unique file per test to avoid stale VFS/Document state from prior tests in the same class instance.
    val fileName = "${testInfo.testMethod.get().name}.$extension"
    val nioFile = tempDir.resolve(fileName).also { it.writeText(input) }
    val psiFile: PsiFile = timeoutRunBlocking {
      val vf: VirtualFile = withContext(Dispatchers.EDT) {
        edtWriteAction {
          checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(nioFile)) {
            "Failed to refresh VFS for $nioFile"
          }
        }
      }
      readAction {
        checkNotNull(PsiManager.getInstance(project).findFile(vf)) { "No PsiFile for $nioFile" }
      }
    }

    WriteCommandAction.runWriteCommandAction(project) {
      CodeStyleManager.getInstance(project).reformat(psiFile)
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    val actual = runReadAction { psiFile.text }
    assertEquals(expected, actual, "Reformatted text differs for $fileName")
  }
}
