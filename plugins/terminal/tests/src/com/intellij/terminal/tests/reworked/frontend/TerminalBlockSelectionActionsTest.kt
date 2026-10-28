// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.actions.TerminalPlatformDataKeys
import com.intellij.terminal.frontend.view.impl.TerminalEditorFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.JBTerminalSystemSettingsProvider
import org.jetbrains.plugins.terminal.block.ui.getClipboardText
import org.jetbrains.plugins.terminal.util.terminalProjectScope
import org.junit.jupiter.api.Test

/**
 * Checks the terminal actions for a block selection whose last line ends before the block.
 * The caret of that line has no selection, and it is the primary caret.
 */
@TestApplication
internal class TerminalBlockSelectionActionsTest {
  companion object {
    private val projectFixture = projectFixture()

    private val TEXT = """
      # abcdef
      #
      # mnopqr
      #
    """.trimIndent()
  }

  private val project: Project by projectFixture

  @Test
  fun `copy action copies every line of a block selection that ends on a short line`(): Unit = doTest { editor ->
    editor.selectionModel.setBlockSelection(LogicalPosition(0, 4), LogicalPosition(3, 7))

    performAction(editor, "Terminal.CopySelectedText")

    assertThat(getClipboardText()).isEqualTo("cde\n\nopq\n")
  }

  @Test
  fun `escape action removes a block selection that ends on a short line`(): Unit = doTest { editor ->
    editor.selectionModel.setBlockSelection(LogicalPosition(0, 4), LogicalPosition(3, 7))

    performAction(editor, "Terminal.Escape")

    assertThat(editor.selectionModel.hasSelection(true)).isFalse()
  }

  private fun doTest(block: (Editor) -> Unit): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val scope = terminalProjectScope(project).childScope("TerminalOutputEditor")
    try {
      val editor = TerminalEditorFactory.createOutputEditor(project, JBTerminalSystemSettingsProvider(), scope)
      editor.document.setText(TEXT)
      block(editor)
    }
    finally {
      scope.cancel()
    }
  }

  /**
   * Runs the action as a shortcut does: the action must be enabled after its update.
   */
  private fun performAction(editor: Editor, actionId: String) {
    val action = ActionManager.getInstance().getAction(actionId) ?: error("Unknown action: $actionId")
    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(TerminalPlatformDataKeys.EDITOR_KEY, editor)
      .build()
    val event = AnActionEvent.createEvent(action, context, null, "", ActionUiKind.NONE, null)
    ActionUtil.updateAction(action, event)
    assertThat(event.presentation.isEnabledAndVisible)
      .describedAs("$actionId is not enabled and visible")
      .isTrue()
    ActionUtil.performAction(action, event)
  }
}
