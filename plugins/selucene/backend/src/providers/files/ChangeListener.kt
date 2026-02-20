package com.intellij.selucene.backend.providers.files

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


    val filesToReindex = events.asSequence().flatMap { event ->
      when (event) {
        is VFileCreateEvent -> listOfNotNull(event.file)
        is VFileDeleteEvent -> listOfNotNull(event.file)
        is VFileCopyEvent -> listOfNotNull(event.file)
        // TODO somehow inform the FileIndex that the moved/renamed file is gone. Currently it only adds the new file to the index.
        is VFileMoveEvent -> {
          listOfNotNull(event.oldParent.findChild(event.file.name), event.file)}
        is VFilePropertyChangeEvent -> {
          if (event.propertyName == VirtualFile.PROP_NAME) {
            listOfNotNull(event.file) // Both old and new location use the same VFile reference
          }
          else {
            emptyList()
          }
        }
        else -> emptyList()
      }
    }
      .toList()

    if (filesToReindex.isEmpty()) { return null }

    projects.forEach { project ->
      FileIndex.getInstance(project).scheduleIndexingOp(LuceneFileIndexOperation.ReindexFiles(filesToReindex))
    }

    return null
  }
}