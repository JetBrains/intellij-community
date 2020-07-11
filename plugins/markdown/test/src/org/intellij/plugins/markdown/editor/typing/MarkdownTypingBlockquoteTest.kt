// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.typing

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownTypingBlockquoteTest : LightPlatformCodeInsightTestCase() {
  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/editor/typing/blockquote/"
  }

  fun testOneLine() = doTest()
  fun testOneLineInText() = doTest()

  fun testFewLine() = doTest()
  fun testFewLineInMiddle() = doTest()
  fun testFewLineInText() = doTest()

  fun testInList() = doTest()
  fun testInListInMiddle() = doTest()
  fun testInListInText() = doTest()

  fun testInInnerList() = doTest()
  fun testInInnerListInMiddle() = doTest()
  fun testInInnerListInText() = doTest()

  fun testInListMixed() = doTest()
  fun testInListMixedInMiddle() = doTest()
  fun testInListMixedInText() = doTest()

  fun testInListReverseMixed() = doTest()
  fun testInListReverseMixedInMiddle() = doTest()
  fun testInListReverseMixedInText() = doTest()

  private fun doTest(text: String = "\n") {
    val testName = getTestName(true)
    configureByFile("$testName.before.md")
    type("\n")
    checkResultByFile("$testName.after.md")
  }
}