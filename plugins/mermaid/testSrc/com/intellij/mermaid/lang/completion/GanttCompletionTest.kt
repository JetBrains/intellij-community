package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class GanttCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/diagrams/gantt")

  private val atLineKeywords = arrayOf("done", "active", "crit", "after", "milestone")

  private val topLevelKeywords = arrayOf("title", "section", "dateFormat", "excludes", "includes", "axisFormat")

  @Test
  fun `test right after header`(): Unit = doTest("title")

  @Test
  fun `test at line`(): Unit = doTest(*topLevelKeywords, *atLineKeywords)

  @Test
  fun `test inside line`(): Unit = doTest(*atLineKeywords)

  private fun doTest(vararg variants: String) {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
