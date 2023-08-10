package com.intellij.mermaid.lang.intention.declarations

import com.intellij.mermaid.lang.MermaidBaseTestCase

class GitgraphAnnotatorTest : MermaidBaseTestCase("intention/declarations") {
  fun `test merge`() = doTest()

  fun `test checkout`() = doTest()

  fun `test cherry-pick`() = doTest()

  fun `test commit`() = doTest()

  fun `test branch`() = doTest()

  fun `test no conflicts`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.configureByFile("${testName}.mermaid")
    myFixture.checkHighlighting()
  }
}
