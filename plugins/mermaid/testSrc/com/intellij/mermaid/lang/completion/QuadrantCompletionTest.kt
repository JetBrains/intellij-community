package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class QuadrantCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/diagrams/quadrant")

  private val keywords = arrayOf("x-axis", "y-axis", "quadrant-1", "quadrant-2", "quadrant-3", "quadrant-4")

  @Test
  fun `test right after header`(): Unit = doTest("title")

  @Test
  fun `test at line`(): Unit = doTest("title", *keywords)

  @Test
  fun `test inside line`(): Unit = doTest()

  private fun doTest(vararg variants: String) {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
