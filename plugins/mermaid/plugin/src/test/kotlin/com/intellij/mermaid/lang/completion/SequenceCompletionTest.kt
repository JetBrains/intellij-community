package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class SequenceCompletionTest : MermaidBaseTestCase("completion/diagrams/sequence") {
  private val keywords =
    arrayOf(
      "loop",
      "alt",
      "opt",
      "par",
      "par_over",
      "rect",
      "critical",
      "break",
      "box",
      "participant",
      "actor",
      "autonumber",
      "title"
    )

  fun `test at top level`() = doTest(*keywords)

  fun `test inside alt block`() = doTest(*keywords, "else", "end")

  fun `test inside par block`() = doTest(*keywords, "and", "end")

  fun `test inside critical block`() = doTest(*keywords, "option", "end")

  fun `test inside loop block`() = doTest(*keywords, "end")

  fun `test inside box block`() = doTest("participant", "actor", "end")

  fun `test after autonumber`() = doTest("off")

  fun `test right after header`() = doTest("title")

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
