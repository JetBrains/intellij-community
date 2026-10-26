package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class SequenceCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/diagrams/sequence")

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

  @Test
  fun `test at top level`(): Unit = doTest(*keywords)

  @Test
  fun `test inside alt block`(): Unit = doTest(*keywords, "else", "end")

  @Test
  fun `test inside par block`(): Unit = doTest(*keywords, "and", "end")

  @Test
  fun `test inside critical block`(): Unit = doTest(*keywords, "option", "end")

  @Test
  fun `test inside loop block`(): Unit = doTest(*keywords, "end")

  @Test
  fun `test inside box block`(): Unit = doTest("participant", "actor", "end")

  @Test
  fun `test after autonumber`(): Unit = doTest("off")

  @Test
  fun `test right after header`(): Unit = doTest("title")

  private fun doTest(vararg variants: String) {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
