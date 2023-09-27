package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.jetbrains.performancePlugin.commands.OpenFileCommand.Companion.findFile

/**
 * Command reloads files from disk
 * Example: %reloadFromDisk filePath1 filePath2
 */
class ReloadFromDiskCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME: String = "reloadFromDisk"
    const val PREFIX: String = CMD_PREFIX + NAME
  }

  // Need for driver call
  @Suppress("UNUSED")
  constructor() : this(text = "", line = 0)

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    synchronizeFiles(extractCommandList(PREFIX, " "), project)
  }

  // Calls from driver
  fun synchronizeFiles(filePaths: List<String>) {
    synchronizeFiles(filePaths, ProjectManager.getInstance().openProjects.single())
  }

  private fun synchronizeFiles(filePaths: List<String>, project: Project) {
    val files = filePaths.map {
      findFile(it, project) ?: error(
        "File not found $it")
    }
    if (files.isEmpty()) return

    for (file in files) {
      if (file.getFileSystem() is ArchiveFileSystem) {
        (file.getFileSystem() as ArchiveFileSystem).clearArchiveCache(file)
      }
      if (file.isDirectory()) {
        file.getChildren()
      }
      if (file is NewVirtualFile) {
        file.markClean()
        file.markDirtyRecursively()
      }
    }

    RefreshQueue.getInstance().refresh(false, true, { postRefresh(project, files) }, files)
  }

  private fun postRefresh(project: Project, files: Collection<VirtualFile>) {
    val localFiles = files.filter { f: VirtualFile -> f.isInLocalFileSystem }
    if (!localFiles.isEmpty()) {
      VcsFacade.getInstance().markFilesDirty(project, localFiles)
    }
  }

  override fun getName(): String {
    return NAME
  }
}