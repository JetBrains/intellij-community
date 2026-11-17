// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.editor.MarkdownCodeInsightSettingsRule
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkdownListItemTabHandlerTest: LightPlatformCodeInsightTestCase() {
  // Test data in this class was written for marker-width indentation.
  override fun setUp() {
    super.setUp()
    CodeStyle.getSettings(project).getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).USE_FIXED_INDENTS_FOR_SUBLISTS = false
  }

  @Rule
  @JvmField
  val rule = MarkdownCodeInsightSettingsRule { it.renumberListsOnType = true }

  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/editor/lists/tab/"

  @Test
  fun testCreateSubItemWithChildren() = doTest()

  @Test
  fun testCreateSubItemWithChildrenInBlockQuote() = doTest()

  @Test
  fun testHeterogeneousDocument() = doTest()

  @Test
  fun testComplexListItemContent() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    configureByFile("$testName.md")
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByFile("$testName-after.md")
  }
}