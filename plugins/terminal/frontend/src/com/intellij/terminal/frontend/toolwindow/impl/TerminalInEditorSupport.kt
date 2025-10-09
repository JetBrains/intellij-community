package com.intellij.terminal.frontend.toolwindow.impl

import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.content.ToolWindowInEditorSupport
import com.intellij.terminal.frontend.editor.TerminalViewVirtualFile
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager
import com.intellij.terminal.frontend.toolwindow.findTabByContent
import com.intellij.terminal.frontend.toolwindow.impl.TerminalToolWindowTabsManagerImpl.Companion.TAB_DETACHED_KEY
import com.intellij.ui.content.Content
import javax.swing.JSplitPane
import javax.swing.SwingConstants

internal class TerminalInEditorSupport : ToolWindowInEditorSupport {
  override fun canOpenInEditor(project: Project, content: Content): Boolean {
    return TerminalToolWindowTabsManager.getInstance(project).findTabByContent(content) != null
  }

  override fun openInEditor(content: Content, targetWindow: EditorWindow, dropSide: Int) {
    val project = targetWindow.owner.manager.project
    val terminalTab = TerminalToolWindowTabsManager.getInstance(project).findTabByContent(content) ?: return
    openReworkedTerminalInEditor(terminalTab, targetWindow, dropSide)
  }

  private fun openReworkedTerminalInEditor(
    tab: TerminalToolWindowTab,
    editorWindow: EditorWindow,
    dropSide: Int,
  ) {
    tab.content.putUserData(TAB_DETACHED_KEY, Unit)
    val file = TerminalViewVirtualFile(tab.view)

    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)
    try {
      if (dropSide != -1 && dropSide != SwingConstants.CENTER) {
        editorWindow.split(
          orientation = if (dropSide == SwingConstants.BOTTOM || dropSide == SwingConstants.TOP) JSplitPane.VERTICAL_SPLIT else JSplitPane.HORIZONTAL_SPLIT,
          forceSplit = true,
          virtualFile = file,
          focusNew = true,
          fileIsSecondaryComponent = dropSide != SwingConstants.LEFT && dropSide != SwingConstants.TOP,
        )
      }
      else {
        val manager = editorWindow.manager
        manager.openFile(file, editorWindow, FileEditorOpenOptions(requestFocus = true))
      }
    }
    finally {
      file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, null)
    }
  }
}