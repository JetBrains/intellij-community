package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class PieCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/diagrams/pie")

  @Test
  fun `test right after header`(): Unit = doTest("showData", "title")

  @Test
  fun `test at first line`(): Unit = doTest("showData", "title")

  @Test
  fun `test at mid line`(): Unit = doTest("title")

  @Test
  fun `test inside statement`(): Unit = doTest()

  private fun doTest(vararg variants: String) {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
