package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class XYChartCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/diagrams/xychart")

  private val keywords = arrayOf("x-axis", "y-axis", "bar", "line")
  private val orientation = arrayOf("horizontal", "vertical")

  @Test
  fun `test right after header`(): Unit = doTest("title", *orientation)

  @Test
  fun `test at line`(): Unit = doTest("title", *keywords)

  @Test
  fun `test inside line`(): Unit = doTest()

  private fun doTest(vararg variants: String) {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
