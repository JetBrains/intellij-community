package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class MarkdownCodeFenceCompletionTest : MermaidBaseTestCase("completion/markdown") {
  fun `test at first line`() = doTest()

  fun `test at mid line`() = doTest()


  fun `test at last line`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.md", "mermaid")
  }
}
