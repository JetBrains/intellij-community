package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class ClassCompletionTest : MermaidBaseTestCase("completion/diagrams/class") {
  private val directions = arrayOf("LR", "RL", "TB", "BT")
  private val keywords = arrayOf("class", "direction", "namespace", "style")

  fun `test at top level`() = doTest(*keywords)

  fun `test inside class`() = doTest()

  fun `test annotation`() = doTest("interface", "abstract", "service", "enumeration")

  fun `test at top level with frontmatter`() = doTest(*keywords)

  fun `test at top level at mid line with frontmatter`() = doTest(*keywords)

  fun `test directions`() = doTest(*directions)

  private fun doTest(vararg variants: String) {
    val testName = getTestName(true)
    myFixture.testCompletionVariants("${testName}.mermaid", *variants)
  }
}
