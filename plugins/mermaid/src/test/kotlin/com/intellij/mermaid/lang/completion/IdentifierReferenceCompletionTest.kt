package com.intellij.mermaid.lang.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class IdentifierReferenceCompletionTest : BasePlatformTestCase() {

  fun `test completion for reference without declaration`() = doTest()

  fun `test completion for declaration without usages`() = doTest()

  fun `test completion for multi declaration`() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.testCompletion("${testName}_before.mermaid", "${testName}_after.mermaid")
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "src/test/resources/completion"
  }
}
