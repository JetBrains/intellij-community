// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.action

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.TerminalPromotedDumbAwareAction
import org.jetbrains.plugins.terminal.block.history.CommandHistoryPresenter.Companion.isTerminalCommandHistory
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.blockTerminalController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isOutputEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isPromptEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.isReworkedTerminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.promptController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.selectionController
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalEditor
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils.terminalSession

/**
 * A handler for the Escape shortcut for the reworked terminal
 *
 * Terminal extensions like AI should register their own handlers if they need to perform
 * something special on Escape (for example, cancel the AI prompt).
 * To ensure that the correct handler is invoked,
 * the [order] property should be used.
 *
 * Note that these extensions are only used for the reworked (gen2) terminal.
 */
@ApiStatus.Experimental
interface TerminalEscapeHandler {
  /**
   * Defines the order in which the handlers are considered
   *
   * The lower the number, the higher the priority of the handler.
   * The handler with the lowest order among the enabled ones will
   * eventually be executed.
   *
   * The currently reserved values are:
   * - 100 - close the active completion popup;
   * - 150 - cancel the AI prompt (implemented only in gen1 terminal);
   * - 200 - cancel selection and focus the prompt;
   * - 300 - cancel the active search;
   * - 500 - leave the terminal tool window and focus the code editor.
   *
   * As a rule of thumb, values divisible by 100 are reserved for the main terminal plugin,
   * values divisible by 10 are reserved for JetBrains plugins,
   * other values are reserved for third party plugins.
   */
  val order: Int

  /**
   * Checks whether this handler is enabled for this action event
   */
  fun isEnabled(e: AnActionEvent): Boolean

  /**
   * Executes the action corresponding to this handler
   *
   * This method is only called if the previous [isEnabled] call returned `true`.
   * If several handlers returned `true`, then the one with the lowest [order]
   * will be executed.
   */
  fun execute(e: AnActionEvent)
}

internal val TERMINAL_ESCAPE_HANDLER_EP: ExtensionPointName<TerminalEscapeHandler> =
  ExtensionPointName.create("org.jetbrains.plugins.terminal.escapeHandler")

internal class TerminalEscapeAction : TerminalPromotedDumbAwareAction() {
  // order matters, because only the first enabled handler will be executed
  private val handlers: List<Handler> = listOf(
    CloseHistoryHandler(),
    SelectPromptHandler(),
    CloseSearchHandler(),
    SelectEditorHandler(),
  )

  override fun actionPerformed(e: AnActionEvent) {
    val handler = handlers(e).find { it.isEnabled(e) } ?: return
    handler.execute(e)
  }

  override fun update(e: AnActionEvent) {
    val editor = e.terminalEditor
    if (editor?.isPromptEditor != true && editor?.isOutputEditor != true && editor?.isReworkedTerminalEditor != true) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isEnabledAndVisible = handlers(e).any { it.isEnabled(e) }
  }

  private fun handlers(e: AnActionEvent): List<TerminalEscapeHandler> {
    return if (e.terminalEditor?.isReworkedTerminalEditor == true) {
      TERMINAL_ESCAPE_HANDLER_EP.extensionList.sortedBy { it.order }
    }
    else {
      handlers.map { legacyHandler -> object : TerminalEscapeHandler {
        override val order: Int = 0
        override fun isEnabled(e: AnActionEvent): Boolean = legacyHandler.isEnabled(e)
        override fun execute(e: AnActionEvent) = legacyHandler.execute(e)
      }}
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  /**
   * Promote only if this action is in the list.
   * It allows external actions to suppress this action and execute their own instead.
   * For example, [com.intellij.ml.llm.terminal.TerminalTextToCommandAction].
   */
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    return actions.filterIsInstance<TerminalEscapeAction>()
  }

  private interface Handler {
    fun execute(e: AnActionEvent)

    /** It is assumed that [com.intellij.openapi.editor.Editor] is not null, and it is a prompt or output editor */
    fun isEnabled(e: AnActionEvent): Boolean
  }

  private class CloseHistoryHandler : Handler {
    override fun execute(e: AnActionEvent) {
      e.promptController?.onCommandHistoryClosed()
    }

    override fun isEnabled(e: AnActionEvent): Boolean {
      return e.terminalEditor?.isPromptEditor == true && LookupManager.getActiveLookup(e.terminalEditor)?.isTerminalCommandHistory == true
    }
  }

  private class SelectPromptHandler : Handler {
    override fun execute(e: AnActionEvent) {
      e.selectionController?.clearSelection()
    }

    override fun isEnabled(e: AnActionEvent): Boolean {
      val editor = e.terminalEditor ?: return false
      val selectionController = e.selectionController ?: return false
      return editor.isOutputEditor && (selectionController.primarySelection != null || editor.selectionModel.hasSelection())
    }
  }

  private class CloseSearchHandler : Handler {
    override fun execute(e: AnActionEvent) {
      e.blockTerminalController?.finishSearchSession()
    }

    override fun isEnabled(e: AnActionEvent): Boolean {
      return e.blockTerminalController?.searchSession != null
    }
  }

  private class SelectEditorHandler : Handler {
    override fun execute(e: AnActionEvent) {
      ToolWindowManager.getInstance(e.project!!).activateEditorComponent()
    }

    override fun isEnabled(e: AnActionEvent): Boolean {
      val terminalModel = e.terminalSession?.model ?: return false
      return e.project != null
             && LookupManager.getActiveLookup(e.terminalEditor) == null
             // the terminal can be located in the Editor tab, so in this case we also should do nothing
             && e.getData(PlatformDataKeys.TOOL_WINDOW) != null
             && AdvancedSettings.getBoolean("terminal.escape.moves.focus.to.editor")
             && (e.terminalEditor?.isPromptEditor == true
                 // Or enable it in output, but only when command is running
                 // In alternate mode, escape action should be sent to the terminal process, so disable the action in this case.
                 || terminalModel.isCommandRunning && !terminalModel.useAlternateBuffer)
    }
  }
}
