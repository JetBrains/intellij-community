package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.searchEverywhereMl.semantics.indices.IndexableEntity
import com.intellij.util.containers.CollectionFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class SemanticSearchFileContentChangeListener<E : IndexableEntity>(val project: Project) {
  private val fileIdToEntityCounts: MutableMap<VirtualFilePointer, Array<Pair<String, Int>>> = CollectionFactory.createSmallMemoryFootprintMap()
  private val mutex = ReentrantLock()

  abstract fun getStorage(): FileContentBasedEmbeddingsStorage<E>
  abstract fun getEntity(id: String): E

  fun processEvents(events: List<VFileEvent>) {
    for (event in events) {
      when (event) {
        is VFileContentChangeEvent -> {
          if (!event.file.isFile) return
          val file = event.file
          if (ProjectFileIndex.getInstance(project).isInSourceContent(file)) {
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
            // psiFile may contain outdated structure, so we manually create a new PSI file from a document text:
            val newPsiFile = file.findDocument()?.let { PsiFileFactory.getInstance(project).createFileFromText(it.text, psiFile) }
                             ?: psiFile
            val entities = getStorage().traversePsiFile(newPsiFile)
            inferEntityDiff(file, entities)
          }
        }
        is VFileDeleteEvent -> {
          if (event.file.isDirectory) return

          val contentSourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots
          // We can't use `isInSourceContent` here, because there is no file after delete operation
          if (contentSourceRoots.any { VfsUtilCore.getRelativePath(event.file, it) != null }) {
            inferEntityDiff(event.file, emptyList())
          }
        }
        else -> {}
      }
    }
  }

  fun addEntityCountsForFile(file: VirtualFile, symbols: List<E>) = mutex.withLock {
    fileIdToEntityCounts[getFileId(file)] = symbols.groupingBy { it.id.intern() }.eachCount().toList().toTypedArray()
  }

  private fun getFileId(file: VirtualFile) = VirtualFilePointerManager.getInstance().create(file, getStorage(), null)

  private fun inferEntityDiff(file: VirtualFile, entities: List<E>) = mutex.withLock {
    val entityCounts = entities.groupingBy { it.id.intern() }.eachCount()
    val fileId = getFileId(file)
    val oldEntityCounts = fileIdToEntityCounts[fileId] ?: emptyArray()
    for ((entityId, count) in entityCounts) {
      val oldCount = oldEntityCounts.find { it.first == entityId }?.second ?: 0
      if (count > oldCount) {
        getStorage().run { repeat(count - oldCount) { addEntity(getEntity(entityId)) } }
      }
    }
    for ((entityId, oldCount) in oldEntityCounts) {
      val count = entityCounts.getOrDefault(entityId, 0)
      if (oldCount > count) {
        getStorage().run { repeat(oldCount - count) { deleteEntity(getEntity(entityId)) } }
      }
    }
    if (entityCounts.isEmpty()) {
      fileIdToEntityCounts.remove(fileId)
    }
    else {
      fileIdToEntityCounts[fileId] = entityCounts.toList().toTypedArray()
    }
  }
}