package com.intellij.mermaid.lang.intention.unused_attributes

import com.intellij.mermaid.lang.MermaidBaseTestCase
import com.intellij.mermaid.lang.intention.UnusedAttributesInspection

class UnusedAttributesInspectionTest : MermaidBaseTestCase("intention/unused_attributes") {
  fun `test journey`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.configureByFile("${testName}.mermaid")
    myFixture.enableInspections(UnusedAttributesInspection())
    myFixture.checkHighlighting()
  }
}
