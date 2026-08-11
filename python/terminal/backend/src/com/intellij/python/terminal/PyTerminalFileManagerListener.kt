package com.intellij.python.terminal

import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.python.terminal.shared.updateCurrentVenvPath

internal class PyTerminalFileManagerListener : FileEditorManagerListener {
  override fun selectionChanged(event: FileEditorManagerEvent) {
    val project = event.manager.project
    val file = event.manager.selectedFiles.firstOrNull() ?: return
    val workingDir = pyTerminalDefaultWorkingDirectory(project, file) ?: return

    updateCurrentVenvPath(project, workingDir)
  }
}
