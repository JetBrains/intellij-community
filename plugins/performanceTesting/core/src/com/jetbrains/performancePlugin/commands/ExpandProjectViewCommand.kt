// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Wait till a project tree is fully initialized.
 * projectView#cachedNodesLoaded might be missing if there are not cached nodes
 * Command should be executed as soon as possible since project view initialization happens very early and the command might hang.
 */
class ExpandProjectViewCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: String = CMD_PREFIX + "expandProjectView"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val file = OpenFileCommand.findFile(extractCommandArgument(PREFIX), context.project)
    val mutex = Mutex(true)
    withContext(Dispatchers.EDT) {
      ProjectView.getInstance(context.project).selectCB(null, file, false).doWhenProcessed {
        mutex.unlock()
      }
    }
    mutex.lock()
  }
}