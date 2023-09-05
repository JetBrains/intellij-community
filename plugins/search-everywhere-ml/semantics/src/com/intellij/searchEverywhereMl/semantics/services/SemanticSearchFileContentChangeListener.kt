package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.searchEverywhereMl.semantics.indices.IndexableEntity
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class SemanticSearchFileContentChangeListener<E : IndexableEntity>(val project: Project) {
  private val projectDir = project.guessProjectDir()

  private val fileIdToEntityCounts: MutableMap<String, Map<String, Int>> = mutableMapOf()
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
            val fileId = getFileId(file) ?: return
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
            // psiFile may contain outdated structure, so we manually create a new PSI file from a document text:
            val newPsiFile = file.findDocument()?.let { PsiFileFactory.getInstance(project).createFileFromText(it.text, psiFile) }
                             ?: psiFile
            val entities = getStorage().traversePsiFile(newPsiFile)
            inferEntityDiff(fileId, entities)
          }
        }
        is VFileDeleteEvent -> {
          if (event.file.isDirectory) return

          val contentSourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots
          // We can't use `isInSourceContent` here, because there is no file after delete operation
          if (contentSourceRoots.any { VfsUtilCore.getRelativePath(event.file, it) != null }) {
            val fileId = getFileId(event.file) ?: return
            inferEntityDiff(fileId, emptyList())
          }
        }
        else -> {}
      }
    }
  }

  fun addEntityCountsForFile(file: VirtualFile, symbols: List<E>) = mutex.withLock {
    val fileId = getFileId(file) ?: return
    fileIdToEntityCounts[fileId] = symbols.groupingBy { it.id }.eachCount()
  }

  private fun getFileId(file: VirtualFile) = VfsUtilCore.getRelativePath(file, projectDir!!)

  private fun inferEntityDiff(fileId: String, entities: List<E>) = mutex.withLock {
    val entityCounts = entities.groupingBy { it.id }.eachCount()
    val oldEntityCounts = fileIdToEntityCounts[fileId] ?: emptyMap()
    for ((entityId, count) in entityCounts) {
      val oldCount = oldEntityCounts.getOrDefault(entityId, 0)
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
    fileIdToEntityCounts[fileId] = entityCounts
  }
}