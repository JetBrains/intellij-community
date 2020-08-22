// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.typing

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownTypingListsTest : LightPlatformCodeInsightTestCase() {
  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/editor/typing/lists/"
  }

  fun testOneItem() {
    doTest()
  }

  fun testFewItems() {
    doTest()
  }

  fun testInnerOneItem() {
    doTest()
  }

  fun testInnerFewItems() {
    doTest()
  }

  fun testFewLinesInItem() {
    doTest()
  }

  fun testInnerFewLinesInItem() {
    doTest()
  }

  fun testFewLinesInItemInMiddle() {
    doTest()
  }

  fun testInnerFewLinesInItemInMiddle() {
    doTest()
  }

  fun testFewLinesInItemInText() {
    doTest()
  }

  fun testInnerFewLinesInItemInText() {
    doTest()
  }

  fun testFewLinesInItemAfterText() {
    doTest()
  }

  private fun doTest(text: String = "\n") {
    val testName = getTestName(true)
    configureByFile("$testName.before.md")
    type("\n")
    checkResultByFile("$testName.after.md")
  }
}