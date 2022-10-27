package com.intellij.mermaid.lang.intention

import com.intellij.mermaid.lang.MermaidBaseTestCase

class NbspAnnotatorTest : MermaidBaseTestCase("intention/nbsp_annotator") {
  fun `test state diagram`() = doTest()

  fun `test flowchart`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.configureByFile("${testName}.mermaid")
    myFixture.checkHighlighting()
  }
}
