package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class QuadrantCompletionTest : MermaidBaseTestCase("completion/diagrams/quadrant") {
  private val keywords = arrayOf("x-axis", "y-axis", "quadrant-1", "quadrant-2", "quadrant-3", "quadrant-4")

  fun `test right after header`() = doTest("title")

  fun `test at line`() = doTest("title", *keywords)

  fun `test inside line`() = doTest()

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
