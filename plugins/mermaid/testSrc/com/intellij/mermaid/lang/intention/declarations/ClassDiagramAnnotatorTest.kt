package com.intellij.mermaid.lang.intention.declarations

import com.intellij.mermaid.lang.MermaidBaseTestCase

class ClassDiagramAnnotatorTest : MermaidBaseTestCase("intention/declarations") {

  fun `test annotation statement`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.configureByFile("${testName}.mermaid")
    myFixture.checkHighlighting()
  }
}
