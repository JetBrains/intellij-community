// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.typing

import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.typeInEditor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/typing/blockquote")
class MarkdownTypingBlockquoteTest {
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
  fun testOneLine(): Unit = doTest()
  @Test
  fun testOneLineInText(): Unit = doTest()

  @Test
  fun testFewLine(): Unit = doTest()
  @Test
  fun testFewLineInMiddle(): Unit = doTest()
  @Test
  fun testFewLineInText(): Unit = doTest()

  @Test
  fun testInList(): Unit = doTest()
  @Test
  fun testInListInMiddle(): Unit = doTest()
  @Test
  fun testInListInText(): Unit = doTest()

  @Test
  fun testInInnerList(): Unit = doTest()
  @Test
  fun testInInnerListInMiddle(): Unit = doTest()
  @Test
  fun testInInnerListInText(): Unit = doTest()

  @Test
  fun testInListMixed(): Unit = doTest()
  @Test
  fun testInListMixedInMiddle(): Unit = doTest()
  @Test
  fun testInListMixedInText(): Unit = doTest()

  @Test
  fun testInListReverseMixed(): Unit = doTest()
  @Test
  fun testInListReverseMixedInMiddle(): Unit = doTest()
  @Test
  fun testInListReverseMixedInText(): Unit = doTest()

  private fun doTest(text: String = "\n"): Unit = timeoutRunBlocking {
    myFixture.configureByFile("$testName.before.md")
    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor("\n")
      myFixture.checkResultByFile("$testName.after.md")
    }
  }
}