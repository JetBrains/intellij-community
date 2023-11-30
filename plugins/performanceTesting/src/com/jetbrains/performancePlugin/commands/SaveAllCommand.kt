package com.jetbrains.performancePlugin.commands

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.playback.PlaybackContext

/**
 * Based on com.intellij.ide.actions.SaveAllAction
 */
class SaveAllCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line){

  companion object {
    const val NAME = "saveAll"
    const val PREFIX = CMD_PREFIX + NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    FileDocumentManager.getInstance().saveAllDocuments()
    if (LightEdit.owns(context.project)) {
      LightEditService.getInstance().saveNewDocuments()
    }
    SaveAndSyncHandler.getInstance().scheduleSave(SaveAndSyncHandler.SaveTask(project = context.project, forceSavingAllSettings = true), forceExecuteImmediately = true)
  }

  override fun getName(): String = NAME
}