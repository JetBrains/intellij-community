// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin

import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.launch
import org.jetbrains.idea.maven.project.MavenFolderResolver
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider

/**
 * The command is the same as click 'Generate sources and Update folders for maven project'
 * Syntax: %updateMavenFolders
 */
class UpdateMavenFoldersCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "updateMavenFolders"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    context.project.also {
      MavenCoroutineScopeProvider.getCoroutineScope(it).launch {
        MavenFolderResolver(it).resolveFoldersAndImport()

      }
    }
  }

  override fun getName(): String {
    return NAME
  }
}