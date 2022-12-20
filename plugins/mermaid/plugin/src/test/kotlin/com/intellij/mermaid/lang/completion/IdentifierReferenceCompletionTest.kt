package com.intellij.mermaid.lang.completion

import com.intellij.mermaid.lang.MermaidBaseTestCase

class IdentifierReferenceCompletionTest : MermaidBaseTestCase("completion/identifier") {

  fun `test completion for reference without declaration`() = doTest()

  fun `test completion for declaration without usages`() = doTest()

  fun `test completion for multi declaration`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.testCompletion("${testName}_before.mermaid", "${testName}_after.mermaid")
  }
}
