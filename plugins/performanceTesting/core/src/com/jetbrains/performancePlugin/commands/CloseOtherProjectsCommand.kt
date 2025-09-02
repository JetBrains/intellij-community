// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls

class CloseOtherProjectsCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: @NonNls String = CMD_PREFIX + "closeOtherProjects"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val openProjects = ProjectManager.getInstance().openProjects
        if (openProjects.none { it != project }) {
          throw IllegalStateException("No other projects are open")
        }
        openProjects.filterNot { it == project }.forEach { ProjectManager.getInstance().closeAndDispose(it) }
        RecentProjectsManager.getInstance().updateLastProjectPath()
      }
    }
  }
}
