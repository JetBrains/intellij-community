package com.intellij.mermaid.lang.intention.undeclared_generic

import com.intellij.mermaid.MermaidBundle
import com.intellij.mermaid.lang.MermaidBaseTestCase
import com.intellij.mermaid.lang.intention.UndeclaredGenericUsageInspection

class RemoveGenericFixTest : MermaidBaseTestCase("intention/undeclared_generic/remove_generic") {
  fun `test not declared in class statement`() = doTest()

  fun `test not declared in relation statement left`() = doTest()

  fun `test not declared in relation statement right`() = doTest()

  fun `test used in annotation`() = doTest()

  fun `test two declarations`() = doTest()

  fun `test generic in second declaration`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.configureByFile("${testName}_before.mermaid")
    myFixture.enableInspections(UndeclaredGenericUsageInspection())

    val targetText = MermaidBundle.message("fix.remove.generic")
    val fix = myFixture.getAllQuickFixes().find { it.text == targetText }
    assertNotNull(fix)
    myFixture.checkPreviewAndLaunchAction(fix!!)
    myFixture.checkResultByFile("${testName}_after.mermaid")
  }
}
