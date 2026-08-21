// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.typing

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownTypingFencesTest : LightPlatformCodeInsightTestCase() {
  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/editor/typing/fences/"
  }

  fun testFencePlain() = doTest()
  fun testFenceInjected() = doTest()

  fun testFenceInjectedFewLines() = doTest()


  fun testFenceInBlockQuote() = doTest()
  fun testFenceInjectedInBlockQuote() = doTest()

  fun testFenceInNestedBlockQuote() = doTest()

  class AutopopupTest : CompletionAutoPopupTestCase() {
    fun testEmptyFenceInReadme() {
      myFixture.configureByText("README.md", "")
      type("```")
      assertNotNull("Lookup should auto-activate", lookup)
      assertEquals("", lookup.currentItem?.lookupString)
      type("\n")
      myFixture.checkResult("```\n<caret>\n```")
    }
  }

  private fun doTest(text: String = "\n") {
    val testName = getTestName(true)
    configureByFile("$testName.before.md")
    type("\n")
    checkResultByFile("$testName.after.md")
  }
}