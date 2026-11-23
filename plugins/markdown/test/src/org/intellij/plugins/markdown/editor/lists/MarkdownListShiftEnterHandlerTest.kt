// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.VisualPosition
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.executeAction
import org.intellij.plugins.markdown.editor.markdownCodeInsightSettingsFixture
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownListShiftEnterHandlerTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val settings = markdownCodeInsightSettingsFixture { it.smartEnterAndBackspace = true }

  @Test
  fun testContinuesCurrentItem(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "- item 1\n  1. item 2<caret>")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
      myFixture.checkResult("- item 1\n  1. item 2\n     <caret>")
    }
  }

  @Test
  fun testDoesNotHandleInsideCodeFence(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "- item\n  ```python\n  def f():\n      x = 1<caret>\n  ```")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
      myFixture.checkResult("- item\n  ```python\n  def f():\n      x = 1\n  <caret>\n  ```")
    }
  }

  @Test
  fun testPreservesBlockQuoteIndent(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "- item\n  > quote<caret>")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
      myFixture.checkResult("- item\n  > quote\n  > <caret>")
    }
  }

  @Test
  fun testDoesNotHandleMultipleCarets(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "- one\n- two<caret>")
    withContext(Dispatchers.EDT) {
      // Caret markers cannot express multiple carets in the expected text.
      myFixture.editor.caretModel.addCaret(VisualPosition(0, 5))
      myFixture.executeAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
      assertEquals("- one\n- \n- two\n- ", myFixture.editor.document.text)
    }
  }

  @Test
  fun testDoesNotHandleEmptyItem(): Unit = timeoutRunBlocking {
    myFixture.configureByText("some.md", "- <caret>")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
      myFixture.checkResult("<caret>")
    }
  }

  @Test
  fun testHandlerIsDisabledWhenSmartEnterAndBackspaceIsDisabled(): Unit = timeoutRunBlocking {
    MarkdownCodeInsightSettings.getInstance().state.smartEnterAndBackspace = false
    myFixture.configureByText("some.md", "- item<caret>")
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
      myFixture.checkResult("- item\n<caret>")
    }
  }
}
