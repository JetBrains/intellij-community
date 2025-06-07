// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

class OpenProjectViewCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "openProjectView"
    private val LOG = logger<OpenProjectViewCommand>()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val parameters = extractCommandArgument(PREFIX).split(",").map { it.trim() }
    val open = if (parameters.isEmpty()) true else parameters[0].toBoolean()
    val windowManager = context.project.serviceAsync<ToolWindowManager>()
    withTimeout(60.seconds) {
      withContext(Dispatchers.EDT) {
        val window = windowManager.getToolWindow(ToolWindowId.PROJECT_VIEW) ?: throw IllegalStateException("Window is not found")
        val isActive = window.isActive || !windowManager.isEditorComponentActive && ToolWindowId.PROJECT_VIEW == windowManager.activeToolWindowId
        if (open) {
          if (isActive) {
            window.activate(null)
          }
          else {
            LOG.warn("Project View has been opened already")
          }
        }
        else {
          if (!isActive) {
            window.hide()
          }
          else {
            LOG.warn("Project View is not open")
          }
        }
      }
    }
  }
}