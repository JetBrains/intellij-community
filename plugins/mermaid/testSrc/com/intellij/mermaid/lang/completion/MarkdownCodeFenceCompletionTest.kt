package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class MarkdownCodeFenceCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/markdown")

  @Test
  fun `test at first line`(): Unit = doTest()

  @Test
  fun `test at mid line`(): Unit = doTest()

  @Test
  fun `test at last line`(): Unit = doTest()

  private fun doTest() {
    val testName = mermaid.getTestName(true)

    mermaid.fixture.testCompletion("${testName}_before.md", "${testName}_after.md")

    val actual = runReadActionBlocking { DebugUtil.psiToString(mermaid.fixture.file, true, true) }
    PlatformTestUtil.assertSameLinesWithFile("${mermaid.testDataPath}${File.separatorChar}${testName}.txt", actual)
  }
}
