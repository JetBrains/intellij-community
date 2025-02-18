package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.vfs.VfsUtil

class WaitForVfsRefreshSelectedEditorCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: String = CMD_PREFIX + "waitForVfsRefreshSelectedEditor"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val editor = FileEditorManager.getInstance(context.project).selectedTextEditor
    if (editor == null) throw IllegalStateException("No selected editor")
    edtWriteAction {
      VfsUtil.markDirtyAndRefresh(false, true, false, editor.virtualFile)
    }
  }

}