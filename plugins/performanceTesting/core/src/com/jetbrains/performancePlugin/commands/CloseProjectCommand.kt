// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

/**
 * Copied from [OpenProjectCommand]
 */
class CloseProjectCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "closeProject"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    withContext(Dispatchers.EDT) {
      // prevent the script from stopping on project close
      context.setProject(null)

      writeIntentReadAction {
        ProjectManager.getInstance().closeAndDispose(project)
      }
    }
  }
}