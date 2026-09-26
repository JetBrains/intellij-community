package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class RequirementCompletionTest : MermaidBaseTestCase("completion/diagrams/requirement") {

  fun `test at top level`() = doTest(
    "requirement",
    "functionalRequirement",
    "interfaceRequirement",
    "performanceRequirement",
    "physicalRequirement",
    "designConstraint",
    "element"
  )

  fun `test risk`() = doTest("high", "medium", "low")

  fun `test verify`() = doTest("analysis", "inspection", "test", "demonstration")

  fun `test relationship`() = doTest("contains", "copies", "derives", "satisfies", "verifies", "refines", "traces")

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
