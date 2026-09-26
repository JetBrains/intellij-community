package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class XYChartCompletionTest : MermaidBaseTestCase("completion/diagrams/xychart") {
  private val keywords = arrayOf("x-axis", "y-axis", "bar", "line")
  private val orientation = arrayOf("horizontal", "vertical")

  fun `test right after header`() = doTest("title", *orientation)

  fun `test at line`() = doTest("title", *keywords)

  fun `test inside line`() = doTest()

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
