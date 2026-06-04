package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class PieCompletionTest : MermaidBaseTestCase("completion/diagrams/pie") {

  fun `test right after header`() = doTest("showData", "title")

  fun `test at first line`() = doTest("showData", "title")

  fun `test at mid line`() = doTest("title")

  fun `test inside statement`() = doTest()

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
