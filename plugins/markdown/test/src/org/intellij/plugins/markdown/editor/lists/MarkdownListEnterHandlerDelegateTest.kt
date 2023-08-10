// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.editor.MarkdownCodeInsightSettingsRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkdownListEnterHandlerDelegateTest: LightPlatformCodeInsightTestCase() {
  @Rule
  @JvmField
  val rule = MarkdownCodeInsightSettingsRule { it.copy(renumberListsOnType = true) }

  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/editor/lists/enter/"

  @Test
  fun testNewItem() = doTest()

  @Test
  fun testNewItemInBlockQuote() = doTest()

  @Test
  fun testNewItemTwoSpacesInMarkerAndDocumentEnd() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    configureByFile("$testName.md")
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByFile("$testName-after.md")
  }
}