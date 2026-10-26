package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class RequirementCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "completion/diagrams/requirement")

  @Test
  fun `test at top level`(): Unit = doTest(
    "requirement",
    "functionalRequirement",
    "interfaceRequirement",
    "performanceRequirement",
    "physicalRequirement",
    "designConstraint",
    "element"
  )

  @Test
  fun `test risk`(): Unit = doTest("high", "medium", "low")

  @Test
  fun `test verify`(): Unit = doTest("analysis", "inspection", "test", "demonstration")

  @Test
  fun `test relationship`(): Unit = doTest("contains", "copies", "derives", "satisfies", "verifies", "refines", "traces")

  private fun doTest(vararg variants: String) {
    val testName = mermaid.getTestName(true)
    mermaid.fixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
