// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Brings the IDE in line with the project files that a test context cache rewound with plain file-system calls.
 *
 * The VFS and the workspace model come from the shared `system` directory and keep the state of the test before.
 * Without this command, the IDE applies the rewound files later and undoes the startup commands that ran before,
 * for example `%setupSDK`, see AT-5404. Starter puts this command first after a rewind.
 * The command ends without an error when the IDE closes the project during the sync.
 *
 * Syntax: %syncRestoredProjectFiles
 */
internal class SyncRestoredProjectFilesCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME: String = "syncRestoredProjectFiles"
    const val PREFIX: String = CMD_PREFIX + NAME
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    try {
      syncRestoredProjectFiles(project)
    }
    catch (e: CancellationException) {
      if (!project.isDisposed) {
        throw e
      }
    }
  }

  private suspend fun syncRestoredProjectFiles(project: Project) {
    // A change in the project files before this sync does not reach the workspace model until a later refresh.
    @Suppress("UnsafeOpenServiceCast")
    (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).awaitSynchronizationWithJpsModel()
    val projectDir = project.guessProjectDir() ?: return
    withContext(Dispatchers.IO) {
      VfsUtil.markDirtyAndRefresh(false, true, true, projectDir)
    }
    StoreReloadManager.getInstance(project).reloadChangedStorageFiles()
  }

  override fun getName(): String = NAME
}
