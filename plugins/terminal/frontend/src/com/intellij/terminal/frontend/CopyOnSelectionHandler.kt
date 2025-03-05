package com.intellij.terminal.frontend

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

internal class CopyOnSelectionHandler(private val settings: JBTerminalSystemSettingsProviderBase) {
  fun install(editor: EditorImpl) {
    editor.selectionModel.addSelectionListener(MySelectionListener())
  }

  private fun copy(text: String) {
    if (!copyToSystemSelectionClipboard(text)) {
      copyToRegularClipboard(text)
    }
  }

  private fun copyToSystemSelectionClipboard(text: String): Boolean {
    try {
      if (!SystemInfo.isLinux) return false
      val clipboard = Toolkit.getDefaultToolkit().systemSelection ?: return false
      clipboard.setContents(StringSelection(text), null)
      return true
    }
    catch (e: Exception) {
      LOG.warn("Failed to copy to the system selection clipboard, falling back to the regular one", e)
      return false
    }
  }

  private fun copyToRegularClipboard(text: String) {
    CopyPasteManager.copyTextToClipboard(text)
  }

  private inner class MySelectionListener : SelectionListener {
    override fun selectionChanged(e: SelectionEvent) {
      // Only perform copying if the editor is focused.
      // In most cases it is, but if the selection was updated through the API it may not be the case.
      if (!settings.copyOnSelect() || e.editor?.contentComponent?.isFocusOwner != true) return
      val text = e.editor.selectionModel.selectedText ?: return
      copy(text)
    }
  }
}

private val LOG = logger<CopyOnSelectionHandler>()