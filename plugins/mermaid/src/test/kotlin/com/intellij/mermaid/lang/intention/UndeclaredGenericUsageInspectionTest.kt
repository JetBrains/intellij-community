package com.intellij.mermaid.lang.intention

import com.intellij.mermaid.lang.MermaidBaseTestCase

class UndeclaredGenericUsageInspectionTest : MermaidBaseTestCase("intention/undeclared_generic") {
  fun `test not declared in class statement`() = doTest()

  fun `test no warning`() = doTest()

  fun `test generic in second declaration`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.configureByFile("${testName}.mermaid")
    myFixture.enableInspections(UndeclaredGenericUsageInspection())
    myFixture.checkHighlighting()
  }
}
