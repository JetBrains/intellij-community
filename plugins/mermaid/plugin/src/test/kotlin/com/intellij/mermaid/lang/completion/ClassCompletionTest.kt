package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class ClassCompletionTest : MermaidBaseTestCase("completion/diagrams/class") {

  fun `test at top level`() = doTest("class", "direction")

  fun `test inside class`() = doTest()

  fun `test annotation`() = doTest("interface", "abstract", "service", "enumeration")

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
