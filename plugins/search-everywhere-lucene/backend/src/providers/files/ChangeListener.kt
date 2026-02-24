package com.intellij.searchEverywhereLucene.backend.providers.files

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

internal class ChangeListener : AsyncFileListener {
  override fun prepareChange(events: List<out VFileEvent>): AsyncFileListener.ChangeApplier? {

    // TODO in Dumb mode, return null
    // Wait until config is loaded and we can expect `ProjectFileIndex.getInstance()` to return the files to index.

    val projects = ProjectManager.getInstance().openProjects
      .filter { project -> project.basePath != null }

    val virtualFilesToIndex = mutableSetOf<VirtualFile>()
    val changedUrls = mutableSetOf<String>()

    // We rely on the fact that the VirtualFiles will not reflect renaming changes here.
    events.asSequence().forEach { event ->
      when (event) {
        is VFileCreateEvent -> changedUrls.add("${event.parent.url}/${event.getChildName()}")
        is VFileDeleteEvent -> virtualFilesToIndex.add(event.file)
        is VFileCopyEvent -> changedUrls.add("${event.newParent.url}/${event.newChildName}")
        is VFileMoveEvent -> {
          virtualFilesToIndex.add(event.file)
          changedUrls.add(event.file.url)
        }
        is VFilePropertyChangeEvent -> {
          if (event.isRename) {
            virtualFilesToIndex.add(event.file)
            changedUrls.add(event.file.url)
          }
        }
      }
    }


    if (virtualFilesToIndex.isEmpty() && changedUrls.isEmpty()) {
      return null
    }

    projects.forEach { project ->
      FileIndex.getInstance(project).scheduleIndexingOp(LuceneFileIndexOperation.ReindexFiles(virtualFilesToIndex,changedUrls))
    }

    return null
  }
}