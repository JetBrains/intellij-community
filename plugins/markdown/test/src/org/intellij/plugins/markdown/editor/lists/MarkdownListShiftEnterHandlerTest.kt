// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.VisualPosition
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.editor.MarkdownCodeInsightSettingsRule
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MarkdownListShiftEnterHandlerTest: LightPlatformCodeInsightTestCase() {
  @Rule
  @JvmField
  val rule = MarkdownCodeInsightSettingsRule { it.smartEnterAndBackspace = true }

  @Test
  fun testContinuesCurrentItem() {
    configureFromFileText("some.md", "- item 1\n  1. item 2<caret>")
    executeAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
    checkResultByText("- item 1\n  1. item 2\n     <caret>")
  }

  @Test
  fun testDoesNotHandleInsideCodeFence() {
    configureFromFileText("some.md", "- item\n  ```python\n  def f():\n      x = 1<caret>\n  ```")
    executeAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
    checkResultByText("- item\n  ```python\n  def f():\n      x = 1\n  <caret>\n  ```")
  }

  @Test
  fun testPreservesBlockQuoteIndent() {
    configureFromFileText("some.md", "- item\n  > quote<caret>")
    executeAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
    checkResultByText("- item\n  > quote\n  > <caret>")
  }

  @Test
  fun testDoesNotHandleMultipleCarets() {
    configureFromFileText("some.md", "- one\n- two<caret>")
    // Caret markers cannot express multiple carets in the expected text.
    editor.caretModel.addCaret(VisualPosition(0, 5))
    executeAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
    assertEquals("- one\n- \n- two\n- ", editor.document.text)
  }

  @Test
  fun testDoesNotHandleEmptyItem() {
    configureFromFileText("some.md", "- <caret>")
    executeAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
    checkResultByText("<caret>")
  }

  @Test
  fun testHandlerIsDisabledWhenSmartEnterAndBackspaceIsDisabled() {
    MarkdownCodeInsightSettings.getInstance().state.smartEnterAndBackspace = false
    configureFromFileText("some.md", "- item<caret>")
    executeAction(IdeActions.ACTION_EDITOR_START_NEW_LINE)
    checkResultByText("- item\n<caret>")
  }
}
