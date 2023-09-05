package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings

class FilesSemanticSearchFileChangeListener(private val project: Project) : BulkFileListener {
  override fun after(events: List<VFileEvent>) {
    if (!SemanticSearchSettings.getInstance().enabledInFilesTab) return
    for (event in events) {
      when (event) {
        is VFileCreateEvent -> {
          event.file?.let { file ->
            if (!file.isFile) return
            if (ProjectFileIndex.getInstance(project).isInSourceContent(file)) {
              FileEmbeddingsStorage.getInstance(project).addEntity(IndexableFile(file))
            }
          }
        }
        is VFileDeleteEvent -> {
          if (event.file.isDirectory) return

          val contentSourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots
          // We can't use `isInSourceContent` here, because there is no file after delete operation
          if (contentSourceRoots.any { VfsUtilCore.getRelativePath(event.file, it) != null }) {
            FileEmbeddingsStorage.getInstance(project).deleteEntity(IndexableFile(event.file))
          }
        }
        is VFilePropertyChangeEvent -> {
          if (!event.file.isFile || !event.isRename) return
          if (ProjectFileIndex.getInstance(project).isInSourceContent(event.file)) {
            val oldName = event.oldValue as String
            FileEmbeddingsStorage.getInstance(project).renameFile(oldName, IndexableFile(event.file))
          }
        }
        else -> {}
      }
    }
  }
}