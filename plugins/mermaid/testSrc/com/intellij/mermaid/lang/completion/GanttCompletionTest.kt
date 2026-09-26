package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class GanttCompletionTest : MermaidBaseTestCase("completion/diagrams/gantt") {
  private val atLineKeywords = arrayOf("done", "active", "crit", "after", "milestone")

  private val topLevelKeywords = arrayOf("title", "section", "dateFormat", "excludes", "includes", "axisFormat")

  fun `test right after header`() = doTest("title")

  fun `test at line`() = doTest(*topLevelKeywords, *atLineKeywords)

  fun `test inside line`() = doTest(*atLineKeywords)

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
