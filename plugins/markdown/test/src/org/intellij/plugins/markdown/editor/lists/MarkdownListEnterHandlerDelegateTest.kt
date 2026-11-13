// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
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
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/lists/enter")
class MarkdownListEnterHandlerDelegateTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val settings = markdownCodeInsightSettingsFixture { it.renumberListsOnType = true }

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true)
  }

  @Test
  fun testNewItem(): Unit = doTest()

  @Test
  fun testNewItemInBlockQuote(): Unit = doTest()

  @Test
  fun testNewItemTwoSpacesInMarkerAndDocumentEnd(): Unit = doTest()

  @Test
  fun testPreservesLeadingSpacesOnRepeatedPaste(): Unit = timeoutRunBlocking {
    myFixture.configureByText("source.md", "```\n<selection>   - item</selection>\n```")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_COPY)

      myFixture.configureByText("target.md", "<!-- paste A1 here -->\n<caret>\n\n<!-- paste A2 here -->")
      myFixture.executeAction(IdeActions.ACTION_EDITOR_PASTE)

      val document = myFixture.editor.document
      myFixture.editor.selectionModel.setSelection(document.getLineStartOffset(1), document.getLineEndOffset(1))
      myFixture.executeAction(IdeActions.ACTION_EDITOR_COPY)
      myFixture.editor.selectionModel.removeSelection()
      myFixture.editor.caretModel.moveToOffset(document.getLineStartOffset(2))
      myFixture.executeAction(IdeActions.ACTION_EDITOR_PASTE)

      myFixture.checkResult("<!-- paste A1 here -->\n   - item\n   - item<caret>\n<!-- paste A2 here -->")
    }
  }

  @Test
  fun testNewCheckBoxItem(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "- [ ] item<caret>")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.checkResult("- [ ] item\n- [ ] <caret>")
    }
  }

  @Test
  fun testNewCheckBoxItemAfterCheckedItem(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "- [x] item<caret>")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.checkResult("- [x] item\n- [ ] <caret>")
    }
  }

  @Test
  fun testNewCheckBoxItemInNumberedList(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "1. [ ] item<caret>")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.checkResult("1. [ ] item\n2. [ ] <caret>")
    }
  }

  @Test
  fun testExitCheckBoxList(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "- [ ] item<caret>")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.checkResult("- [ ] item\n<caret>")
    }
  }

  @Test
  fun testExitCheckedCheckBoxList(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "- [x] <caret>")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.checkResult("<caret>")
    }
  }

  @Test
  fun testNewCheckBoxItemInBlockQuote(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "> - [ ] item<caret>")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.checkResult("> - [ ] item\n> - [ ] <caret>")
    }
  }

  @Test
  fun testNewCheckBoxItemAfterExtraSpaces(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "- [ ]   item<caret>")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.checkResult("- [ ]   item\n- [ ] <caret>")
    }
  }

  @Test
  fun testNewCheckBoxItemWhenRenumberListsOnTypeDisabled(): Unit = timeoutRunBlocking {
    MarkdownCodeInsightSettings.getInstance().state.renumberListsOnType = false
    myFixture.configureByText("some.md", "3. [ ] item<caret>")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.checkResult("3. [ ] item\n4. [ ] <caret>")
    }
  }

  @Test
  fun testUnindentEmptyNestedCheckBoxItem(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "- parent\n  - [ ] child<caret>")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.checkResult("- parent\n  - [ ] child\n- [ ] <caret>")
    }
  }

  @Test
  fun testEnterBetweenListMarkerAndCheckBox(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "- <caret>[ ] item")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.checkResult("- [ ] \n- [ ] <caret>item")
    }
  }

  @Test
  fun testHardLineBreakDoesNotCreateNewItem(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "- item 1  <caret>")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.checkResult("- item 1  \n<caret>")
    }
  }

  @Test
  fun testEnterBeforeItem(): Unit = doTest()

  @Test
  fun testSplitLineInsideItem(): Unit = timeoutRunBlocking {
    myFixture.configureByFile("splitLineInsideItem.md")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_SPLIT)
      myFixture.checkResultByFile("splitLineInsideItem-after.md")
    }
  }

  @Test
  fun testPostProcessWithoutListItem(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "- list item<caret>\n\n# heading")
    withContext(Dispatchers.EDT) {
      var markerInserted = false
      myFixture.editor.caretModel.addCaretListener(object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
          if (!markerInserted && myFixture.editor.document.text.contains("\n- ")) {
            markerInserted = true
            myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("# heading") + 2)
          }
        }
      }, myFixture.testRootDisposable)
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      check(markerInserted)
      myFixture.checkResult("- list item\n- \n\n# heading")
    }
  }

  private fun doTest(): Unit = timeoutRunBlocking {
    myFixture.configureByFile("$testName.md")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.checkResultByFile("$testName-after.md")
    }
  }
}