package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.vfs.VfsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WaitForVfsRefreshCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: String = CMD_PREFIX + "waitForVfsRefresh"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val editor = FileEditorManager.getInstance(context.project).getSelectedTextEditor()
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        VfsUtil.markDirtyAndRefresh(false, true, false, editor?.virtualFile)
      }
    }
  }

}