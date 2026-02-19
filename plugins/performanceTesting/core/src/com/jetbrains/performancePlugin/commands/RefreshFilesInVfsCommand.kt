// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.util.indexing.FileBasedIndex

class RefreshFilesInVfsCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "refreshFilesInVfs"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    FileBasedIndex.getInstance().iterateIndexableFiles({ true }, context.project, null)
    val roots = ProjectRootManagerEx.getInstanceEx(context.project).markRootsForRefresh()
    RefreshQueue.getInstance().refresh(true, roots)
  }
}
