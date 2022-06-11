// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownListItemTabHandlerTest: LightPlatformCodeInsightTestCase() {
  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/editor/lists/tab/"

  fun testCreateSubItemWithChildren() = doTest()

  fun testCreateSubItemWithChildrenInBlockQuote() = doTest()

  fun testHeterogeneousDocument() = doTest()

  fun testComplexListItemContent() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    configureByFile("$testName.md")
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByFile("$testName-after.md")
  }
}