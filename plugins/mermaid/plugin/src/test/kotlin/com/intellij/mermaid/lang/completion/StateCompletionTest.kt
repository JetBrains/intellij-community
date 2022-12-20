package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class StateCompletionTest : MermaidBaseTestCase("completion/diagrams/state") {
  private val keywords = arrayOf("state", "direction", "note")

  fun `test at top level`() = doTest(*keywords)

  fun `test inside state`() = doTest(*keywords, "name")

  fun `test after description`() = doTest("as")

  fun `test after note`() = doTest("right of", "left of")

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
