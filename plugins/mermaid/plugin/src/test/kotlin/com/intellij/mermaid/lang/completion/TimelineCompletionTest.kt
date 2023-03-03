package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class TimelineCompletionTest : MermaidBaseTestCase("completion/diagrams/timeline") {

  fun `test right after header`() = doTest("title")

  fun `test in line`() = doTest("title", "section")

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
