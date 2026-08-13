// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.editor.MarkdownCodeInsightSettingsRule
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkdownListEnterHandlerDelegateTest: LightPlatformCodeInsightTestCase() {
  @Rule
  @JvmField
  val rule = MarkdownCodeInsightSettingsRule { it.renumberListsOnType = true }

  override fun getTestDataPath(): String = MarkdownTestingUtil.TEST_DATA_PATH + "/editor/lists/enter/"

  @Test
  fun testNewItem() = doTest()

  @Test
  fun testNewItemInBlockQuote() = doTest()

  @Test
  fun testNewItemTwoSpacesInMarkerAndDocumentEnd() = doTest()

  @Test
  fun testNewCheckBoxItem() {
    configureFromFileText("some.md", "- [ ] item<caret>")
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByText("- [ ] item\n- [ ] <caret>")
  }

  @Test
  fun testNewCheckBoxItemAfterCheckedItem() {
    configureFromFileText("some.md", "- [x] item<caret>")
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByText("- [x] item\n- [ ] <caret>")
  }

  @Test
  fun testNewCheckBoxItemInNumberedList() {
    configureFromFileText("some.md", "1. [ ] item<caret>")
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByText("1. [ ] item\n2. [ ] <caret>")
  }

  @Test
  fun testExitCheckBoxList() {
    configureFromFileText("some.md", "- [ ] item<caret>")
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByText("- [ ] item\n<caret>")
  }

  @Test
  fun testExitCheckedCheckBoxList() {
    configureFromFileText("some.md", "- [x] <caret>")
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByText("<caret>")
  }

  @Test
  fun testNewCheckBoxItemInBlockQuote() {
    configureFromFileText("some.md", "> - [ ] item<caret>")
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByText("> - [ ] item\n> - [ ] <caret>")
  }

  @Test
  fun testNewCheckBoxItemAfterExtraSpaces() {
    configureFromFileText("some.md", "- [ ]   item<caret>")
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByText("- [ ]   item\n- [ ] <caret>")
  }

  @Test
  fun testNewCheckBoxItemWhenRenumberListsOnTypeDisabled() {
    MarkdownCodeInsightSettings.getInstance().state.renumberListsOnType = false
    configureFromFileText("some.md", "3. [ ] item<caret>")
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByText("3. [ ] item\n4. [ ] <caret>")
  }

  @Test
  fun testUnindentEmptyNestedCheckBoxItem() {
    configureFromFileText("some.md", "- parent\n  - [ ] child<caret>")
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByText("- parent\n  - [ ] child\n- [ ] <caret>")
  }

  @Test
  fun testEnterBetweenListMarkerAndCheckBox() {
    configureFromFileText("some.md", "- <caret>[ ] item")
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByText("- [ ] \n- [ ] <caret>item")
  }

  @Test
  fun testHardLineBreakDoesNotCreateNewItem() {
    configureFromFileText("some.md", "- item 1  <caret>")
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByText("- item 1  \n<caret>")
  }

  @Test
  fun testEnterBeforeItem() = doTest()

  @Test
  fun testSplitLineInsideItem() {
    configureByFile("splitLineInsideItem.md")
    executeAction(IdeActions.ACTION_EDITOR_SPLIT)
    checkResultByFile("splitLineInsideItem-after.md")
  }

  @Test
  fun testPostProcessWithoutListItem() {
    configureFromFileText("some.md", "- list item<caret>\n\n# heading")
    var markerInserted = false
    editor.caretModel.addCaretListener(object : CaretListener {
      override fun caretPositionChanged(event: CaretEvent) {
        if (!markerInserted && editor.document.text.contains("\n- ")) {
          markerInserted = true
          editor.caretModel.moveToOffset(editor.document.text.indexOf("# heading") + 2)
        }
      }
    }, testRootDisposable)
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    check(markerInserted)
    checkResultByText("- list item\n- \n\n# heading")
  }

  private fun doTest() {
    val testName = getTestName(true)
    configureByFile("$testName.md")
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByFile("$testName-after.md")
  }
}