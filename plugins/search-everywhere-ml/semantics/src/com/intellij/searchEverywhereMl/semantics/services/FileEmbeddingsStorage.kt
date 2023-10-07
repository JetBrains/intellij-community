package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR
import com.intellij.platform.ml.embeddings.utils.splitIdentifierIntoTokens
import com.intellij.searchEverywhereMl.semantics.SemanticSearchBundle
import com.intellij.searchEverywhereMl.semantics.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.searchEverywhereMl.semantics.indices.IndexableEntity
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.io.File

/**
 * Thread-safe service for semantic files search.
 * Holds a state with embeddings for each available project file and persists it on disk after calculation.
 * Generates the embeddings for files not present in the loaded state at the IDE startup event if semantic files search is enabled.
 */
@Service(Service.Level.PROJECT)
class FileEmbeddingsStorage(project: Project) : DiskSynchronizedEmbeddingsStorage<IndexableFile>(project), Disposable {
  // At unique path based on project location in a file system
  override val index = DiskSynchronizedEmbeddingSearchIndex(
    project.getProjectCachePath(
      File(SEMANTIC_SEARCH_RESOURCES_DIR)
        .resolve(LocalArtifactsManager.getInstance().getModelVersion())
        .resolve(INDEX_DIR).toString()
    )
  )
  override val indexingTaskManager = EmbeddingIndexingTaskManager(index)

  override val scanningTitle
    get() = SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.files.scanning.label")
  override val setupTitle
    get() = SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.files.generation.label")

  override val spanIndexName = "semanticFiles"

  override val indexMemoryWeight: Int = 1
  override val indexStrongLimit = Registry.intValue("search.everywhere.ml.semantic.indexing.indexable.files.limit")

  init {
    project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, FilesSemanticSearchFileChangeListener(project))
  }

  override fun checkSearchEnabled() = SemanticSearchSettings.getInstance().enabledInFilesTab

  @RequiresBackgroundThread
  override fun getIndexableEntities(): List<IndexableFile> {
    // It's important that we do not block write actions here:
    // If the write action is invoked, the read action is restarted
    return ReadAction.nonBlocking<List<IndexableFile>> {
      buildList {
        ProjectFileIndex.getInstance(project).iterateContent{
            if (it.isFile and it.isInLocalFileSystem) add(IndexableFile(it))
            true
        }
      }
    }.executeSynchronously()
  }

  fun renameFile(oldFileName: String, newFile: IndexableFile) {
    if (!checkSearchEnabled()) return
    indexingTaskManager.scheduleTask(
      EmbeddingIndexingTask.RenameDiskSynchronized(oldFileName.intern(), newFile.id.intern(), newFile.indexableRepresentation.intern())
    )
  }

  companion object {
    private const val INDEX_DIR = "files"

    fun getInstance(project: Project) = project.service<FileEmbeddingsStorage>()
  }

  override fun dispose() = Unit
}

class IndexableFile(file: VirtualFile) : IndexableEntity {
  override val id = file.name.intern()
  override val indexableRepresentation by lazy { splitIdentifierIntoTokens(file.nameWithoutExtension).joinToString(separator = " ") }
}