// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownTypedHandlerDelegateTest: LightPlatformCodeInsightTestCase() {
  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/editor/lists/typing/"

  fun testRenumberOnItemCreation() = doTest()

  fun testRenumberOnItemCreationInBlockQuote() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    configureByFile("$testName.md")
    type(' ')
    checkResultByFile("$testName-after.md")
  }
}