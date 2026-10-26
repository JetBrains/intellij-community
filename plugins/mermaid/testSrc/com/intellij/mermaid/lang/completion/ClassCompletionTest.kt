package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class ClassCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/diagrams/class")

  private val directions = arrayOf("LR", "RL", "TB", "BT")
  private val keywords = arrayOf("class", "direction", "namespace", "style")

  @Test
  fun `test at top level`(): Unit = doTest(*keywords)

  @Test
  fun `test inside class`(): Unit = doTest()

  @Test
  fun `test annotation`(): Unit = doTest("interface", "abstract", "service", "enumeration")

  @Test
  fun `test at top level with frontmatter`(): Unit = doTest(*keywords)

  @Test
  fun `test at top level at mid line with frontmatter`(): Unit = doTest(*keywords)

  @Test
  fun `test directions`(): Unit = doTest(*directions)

  private fun doTest(vararg variants: String) {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
