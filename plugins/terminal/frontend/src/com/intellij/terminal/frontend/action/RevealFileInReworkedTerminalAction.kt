package com.intellij.terminal.frontend.action

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.terminal.TerminalEngine
import org.jetbrains.plugins.terminal.TerminalOptionsProvider
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

/**
 * Copy of [org.jetbrains.plugins.terminal.action.RevealFileInTerminalAction], but frontend-only.
 * Also, the requirement for virtual file to be in the local file system is relaxed.
 *
 * This action is enabled only when [TerminalEngine.REWORKED] is enabled.
 * It is required because the Reworked Terminal should be opened from the frontend.
 *
 * If terminal engine is not [TerminalEngine.REWORKED], then this action will be disabled and original
 * [org.jetbrains.plugins.terminal.action.RevealFileInTerminalAction] will be performed on the backend instead.
 * Classic Terminal requires opening the session with the specified working directory only on backend.
 */
internal class RevealFileInReworkedTerminalAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val file = getSelectedFile(e) ?: return
    val tabState = TerminalTabState()
    tabState.myWorkingDirectory = file.path
    TerminalToolWindowManager.getInstance(project).createNewTab(
      TerminalEngine.REWORKED,
      null,
      tabState,
      null,
    )
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isAvailable(e)
  }

  private fun isAvailable(e: AnActionEvent): Boolean {
    if (TerminalOptionsProvider.instance.terminalEngine != TerminalEngine.REWORKED) {
      return false
    }

    val project = e.project
    val editor = e.getData(CommonDataKeys.EDITOR)
    return project != null && !LightEdit.owns(project) && getSelectedFile(e) != null &&
           (!e.isFromContextMenu || editor == null || !editor.getSelectionModel().hasSelection())
  }

  private fun getSelectedFile(e: AnActionEvent): VirtualFile? {
    return e.getData(CommonDataKeys.VIRTUAL_FILE)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}