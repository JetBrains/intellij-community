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
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/typing/lists")
class MarkdownTypingListsTest {
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
  fun testOneItem() {
    doTest()
  }

  @Test
  fun testFewItems() {
    doTest()
  }

  @Test
  fun testInnerOneItem() {
    doTest()
  }

  @Test
  fun testInnerFewItems() {
    doTest()
  }

  @Test
  fun testFewLinesInItem() {
    doTest()
  }

  @Test
  fun testInnerFewLinesInItem() {
    doTest()
  }

  @Test
  fun testFewLinesInItemInMiddle() {
    doTest()
  }

  @Test
  fun testInnerFewLinesInItemInMiddle() {
    doTest()
  }

  @Test
  fun testFewLinesInItemInText() {
    doTest()
  }

  @Test
  fun testInnerFewLinesInItemInText() {
    doTest()
  }

  @Test
  fun testFewLinesInItemAfterText() {
    doTest()
  }

  private fun doTest(text: String = "\n"): Unit = timeoutRunBlocking {
    myFixture.configureByFile("$testName.before.md")
    withContext(Dispatchers.EDT) {
      myFixture.typeInEditor("\n")
      myFixture.checkResultByFile("$testName.after.md")
    }
  }
}