package com.jetbrains.performancePlugin.commands

import com.intellij.configurationStore.saveProjectsAndApp
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saving project settings and unsaved documents
 */
class SaveDocumentsAndSettingsCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "saveDocumentsAndSettings"

    suspend fun save(project: Project) {
      withContext(CoroutineName("save docs and project settings")) {
        withContext(Dispatchers.EDT) {
          writeIntentReadAction { FileDocumentManager.getInstance().saveAllDocuments() }
        }
        saveProjectsAndApp(forceSavingAllSettings = true, onlyProject = project)
      }
    }
  }

  override suspend fun doExecute(context: PlaybackContext) {
    save(context.project)
  }
}