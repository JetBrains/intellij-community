// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.exp.history.CommandHistoryPresenter
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.blockTerminalController
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.editor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.promptController
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.selectionController
import org.jetbrains.plugins.terminal.exp.TerminalDataContextUtils.terminalSession
import org.jetbrains.plugins.terminal.exp.TerminalPromotedDumbAwareAction

class TerminalEscapeAction : TerminalPromotedDumbAwareAction(), ActionRemoteBehaviorSpecification.Disabled {
  // order matters, because only the first enabled handler will be executed
  private val handlers: List<TerminalEscapeHandler> = listOf(
    CloseHistoryHandler(),
    SelectPromptHandler(),
    CloseSearchHandler(),
    SelectEditorHandler(),
  )

  override fun actionPerformed(e: AnActionEvent) {
    val handler = handlers.find { it.isEnabled(e) } ?: return
    handler.execute(e)
  }

  override fun update(e: AnActionEvent) {
    val editor = e.editor
    if (editor?.isPromptEditor != true && editor?.isOutputEditor != true) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isEnabledAndVisible = handlers.any { it.isEnabled(e) }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  private interface TerminalEscapeHandler {
    fun execute(e: AnActionEvent)

    /** It is assumed that [com.intellij.openapi.editor.Editor] is not null, and it is a prompt or output editor */
    fun isEnabled(e: AnActionEvent): Boolean
  }

  private class CloseHistoryHandler : TerminalEscapeHandler {
    override fun execute(e: AnActionEvent) {
      e.promptController?.onCommandHistoryClosed()
    }

    override fun isEnabled(e: AnActionEvent): Boolean {
      val lookup = LookupManager.getActiveLookup(e.editor) as? UserDataHolder
      return e.editor?.isPromptEditor == true
             && lookup?.getUserData(CommandHistoryPresenter.IS_COMMAND_HISTORY_LOOKUP_KEY) == true
    }
  }

  private class SelectPromptHandler : TerminalEscapeHandler {
    override fun execute(e: AnActionEvent) {
      e.selectionController?.clearSelection()
    }

    override fun isEnabled(e: AnActionEvent): Boolean {
      val editor = e.editor ?: return false
      val selectionController = e.selectionController ?: return false
      return editor.isOutputEditor && (selectionController.primarySelection != null || editor.selectionModel.hasSelection())
    }
  }

  private class CloseSearchHandler : TerminalEscapeHandler {
    override fun execute(e: AnActionEvent) {
      e.blockTerminalController?.finishSearchSession()
    }

    override fun isEnabled(e: AnActionEvent): Boolean {
      return e.blockTerminalController?.searchSession != null
    }
  }

  private class SelectEditorHandler : TerminalEscapeHandler {
    override fun execute(e: AnActionEvent) {
      ToolWindowManager.getInstance(e.project!!).activateEditorComponent()
    }

    override fun isEnabled(e: AnActionEvent): Boolean {
      val terminalModel = e.terminalSession?.model ?: return false
      return e.project != null
             && LookupManager.getActiveLookup(e.editor) == null
             // the terminal can be located in the Editor tab, so in this case we also should do nothing
             && e.getData(PlatformDataKeys.TOOL_WINDOW) != null
             && AdvancedSettings.getBoolean("terminal.escape.moves.focus.to.editor")
             && (e.editor?.isPromptEditor == true
                 // Or enable it in output, but only when command is running
                 // In alternate mode, escape action should be sent to the terminal process, so disable the action in this case.
                 || terminalModel.isCommandRunning && !terminalModel.useAlternateBuffer)
    }
  }
}