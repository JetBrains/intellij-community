package com.intellij.python.terminal

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.terminal.shared.updateCurrentVenvPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PyTerminalFileManagerListener : FileEditorManagerListener {
  override fun selectionChanged(event: FileEditorManagerEvent) {
    val project = event.manager.project
    val file = event.manager.selectedFiles.firstOrNull() ?: return
    project.service<PyTerminalVenvPathUpdater>().selectionChanged(file, ClientId.current)
  }
}

/**
 * Sends the working directory of the selected file to the terminal of the client that selected it.
 *
 * The directory is computed off the EDT, because [pyTerminalDefaultWorkingDirectory] can wait for pending VFS I/O.
 * Only the latest selection is kept.
 */
@Service(Service.Level.PROJECT)
private class PyTerminalVenvPathUpdater(private val project: Project, coroutineScope: CoroutineScope) {
  private class Selection(val file: VirtualFile, val clientId: ClientId)

  private val selections = MutableSharedFlow<Selection>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch(Dispatchers.Default) {
      selections.collectLatest { selection ->
        withContext(selection.clientId.asContextElement()) {
          val workingDir = pyTerminalDefaultWorkingDirectory(project, selection.file) ?: return@withContext
          updateCurrentVenvPath(project, workingDir)
        }
      }
    }
  }

  fun selectionChanged(file: VirtualFile, clientId: ClientId) {
    selections.tryEmit(Selection(file, clientId))
  }
}
