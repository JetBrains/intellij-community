package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase
import com.intellij.psi.impl.DebugUtil
import java.io.File

class MarkdownCodeFenceCompletionTest : MermaidBaseTestCase("completion/markdown") {
  fun `test at first line`() = doTest()

  fun `test at mid line`() = doTest()

  fun `test at last line`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)

    myFixture.testCompletion("${testName}_before.md", "${testName}_after.md")

    val actual = DebugUtil.psiToString(myFixture.file, true, true)
    assertSameLinesWithFile("${testDataPath}${File.separatorChar}${testName}.txt", actual)
  }
}
