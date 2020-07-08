// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.typing

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownTypingParagraphsTest : LightPlatformCodeInsightTestCase() {
  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH + "/editor/typing/paragraphs/"
  }

  fun testEmpty() {
    doTest()
  }

  fun testEmptyLine() {
    doTest()
  }

  fun testOneLineParagraph() {
    doTest()
  }

  fun testFewLineParagraph() {
    doTest()
  }

  fun testFewLineParagraphInMiddle() {
    doTest()
  }

  fun testFewLineParagraphInText() {
    doTest()
  }

  fun testIncorrectlyIndentedParagraphInMiddle() {
    doTest()
  }

  private fun doTest(text: String = "\n") {
    val testName = getTestName(true)
    configureByFile("$testName.before.md")
    type("\n")
    checkResultByFile("$testName.after.md")
  }
}