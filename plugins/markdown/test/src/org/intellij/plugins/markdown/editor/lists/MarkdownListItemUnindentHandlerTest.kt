// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.executeAction
import org.intellij.plugins.markdown.editor.markdownCodeInsightSettingsFixture
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/lists/unindent")
class MarkdownListItemUnindentHandlerTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val settings = markdownCodeInsightSettingsFixture { it.renumberListsOnType = true }

  private lateinit var testName: String

  // Test data in this class was written for marker-width indentation
  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true)
    CodeStyle.getSettings(myFixture.project).getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).USE_FIXED_INDENTS_FOR_SUBLISTS = false
  }

  @Test
  fun testSubItemWithChildren(): Unit = doTest()

  @Test
  fun testSubItemWithChildrenInBlockQuote(): Unit = doTest()

  @Test
  fun testChecklistSubItemWithSubItems(): Unit = doTest()

  @Test
  fun testChecklistSubItemWithSubItemsLargerIndent(): Unit = doTest()

  @Test
  fun testUnindentMovesOnlySelectedItems(): Unit = doTest()

  @Test
  fun testUnselectedItemKeepsNestingOnUnindent(): Unit = doTest()

  @Test
  fun testSubItemsOfUnselectedItemStayAligned(): Unit = doTest()

  // a whole-line selection made with the mouse ends at the start of the next line
  @Test
  fun testItemAfterSelectionEndNotMoved(): Unit = doTest()

  private fun doTest(): Unit = timeoutRunBlocking {
    myFixture.configureByFile("$testName.md")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction("EditorUnindentSelection")
      myFixture.checkResultByFile("$testName-after.md")
    }
  }
}