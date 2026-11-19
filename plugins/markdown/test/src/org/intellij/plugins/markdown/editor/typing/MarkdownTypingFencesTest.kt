// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.typing

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.typeInEditor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/typing/fences")
class MarkdownTypingFencesTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true)
  }

  @Test
  fun testFencePlain(): Unit = doTest()
  @Test
  fun testFenceInjected(): Unit = doTest()

  @Test
  fun testFenceInjectedFewLines(): Unit = doTest()

  @Test
  fun testFenceInBlockQuote(): Unit = doTest()
  @Test
  fun testFenceInjectedInBlockQuote(): Unit = doTest()

  @Test
  fun testFenceInNestedBlockQuote(): Unit = doTest()

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

  private fun doTest(text: String = "\n"): Unit = timeoutRunBlocking {
    myFixture.configureByFile("$testName.before.md")
    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor("\n")
      myFixture.checkResultByFile("$testName.after.md")
    }
  }
}