package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class IdentifierReferenceCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/identifier")

  @Test
  fun `test completion for reference without declaration`(): Unit = doTest()

  @Test
  fun `test completion for declaration without usages`(): Unit = doTest()

  @Test
  fun `test completion for multi declaration`(): Unit = doTest()

  private fun doTest() {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.testCompletion("${testName}_before.mermaid", "${testName}_after.mermaid")
  }
}
