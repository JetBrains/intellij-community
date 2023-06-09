package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

internal class OpenProjectView(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "openProjectView"
    private val LOG = logger<OpenProjectView>()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val windowManager = context.project.serviceAsync<ToolWindowManager>()
    withTimeout(60.seconds) {
      withContext(Dispatchers.EDT) {
        val window = windowManager.getToolWindow(ToolWindowId.PROJECT_VIEW) ?: throw IllegalStateException("Window is not found")
        if (!window.isActive && (windowManager.isEditorComponentActive || ToolWindowId.PROJECT_VIEW != windowManager.activeToolWindowId)) {
          window.activate(null)
          LOG.warn("Project View is opened")
        }
        else {
          LOG.warn("Project View has been opened already")
        }
      }
    }
  }
}