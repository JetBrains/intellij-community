// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.typing

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

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

  @TestApplication
  class AutopopupTest {
    companion object {
      private val projectFixture = projectFixture(openAfterCreation = true)
    }

    private val myFixture by codeInsightModuleFixture(projectFixture)

    private val lookup: LookupImpl? get() = CompletionAutoPopupTestUtil.getLookup(myFixture)

    private fun type(s: String) = CompletionAutoPopupTestUtil.type(myFixture, s)

    private fun runTest(block: () -> Unit) = CompletionAutoPopupTestUtil.runWithAutoPopupEnabled<Throwable>(myFixture, block)

    @Test
    fun testEmptyFenceInReadme(): Unit = runTest {
      myFixture.configureByText("README.md", "")
      type("```")
      assertNotNull(lookup, "Lookup should auto-activate")
      assertEquals("", lookup!!.currentItem?.lookupString)
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