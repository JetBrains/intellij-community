package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class BlockCompletionTest : MermaidBaseTestCase("completion/diagrams/block") {
  fun `test at line`() = doTest("block", "columns", "space")

  fun `test after columns`() = doTest("auto", "space")

  fun `test after block`() = doTest("end", "space")

  fun `test inside block`() = doTest("block", "columns", "space")

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
