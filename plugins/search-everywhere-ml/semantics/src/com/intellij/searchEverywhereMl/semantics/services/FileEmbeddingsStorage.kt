package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.*
import com.intellij.searchEverywhereMl.semantics.SemanticSearchBundle
import com.intellij.searchEverywhereMl.semantics.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.searchEverywhereMl.semantics.indices.IndexableEntity
import com.intellij.searchEverywhereMl.semantics.services.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings
import com.intellij.searchEverywhereMl.semantics.utils.splitIdentifierIntoTokens
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.io.File
import java.util.*

/**
 * Thread-safe service for semantic files search.
 * Holds a state with embeddings for each available project file and persists it on disk after calculation.
 * Generates the embeddings for files not present in the loaded state at the IDE startup event if semantic files search is enabled.
 */
@Service(Service.Level.PROJECT)
class FileEmbeddingsStorage(project: Project) : DiskSynchronizedEmbeddingsStorage<IndexableFile>(project) {
  // At unique path based on project location in a file system
  override val index = DiskSynchronizedEmbeddingSearchIndex(
    project.getProjectCachePath(
      File(SEMANTIC_SEARCH_RESOURCES_DIR)
        .resolve(LocalArtifactsManager.getInstance().getModelVersion())
        .resolve(INDEX_DIR).toString()
    )
  )
  override val indexingTaskManager = EmbeddingIndexingTaskManager(index)

  override val scanningTitle = SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.files.scanning.label")
  override val setupTitle = SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.files.generation.label")
  override val spanIndexName = "semanticFiles"

  override val indexMemoryWeight: Int = 1
  override val indexStrongLimit = Registry.intValue("search.everywhere.ml.semantic.indexing.indexable.files.limit")

  init {
    project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, FilesSemanticSearchFileChangeListener(project))
  }

  override fun checkSearchEnabled() = SemanticSearchSettings.getInstance().enabledInFilesTab

  @RequiresBackgroundThread
  override fun getIndexableEntities(): List<IndexableFile> {
    // It's important that we do not block write actions here:
    // If the write action is invoked, the read action is restarted
    return ReadAction.nonBlocking<List<IndexableFile>> {
      buildList {
        ProjectRootManager.getInstance(project).contentSourceRoots.forEach { root ->
          VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
            virtualFile.canonicalFile?.also { if (it.isFile) add(IndexableFile(it)) }
            return@iterateChildrenRecursively true
          }
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
}

@Suppress("unused")  // Registered in the plugin's XML file
class FileSemanticSearchServiceInitializer : ProjectActivity {
  override suspend fun execute(project: Project) {
    // Instantiate service for the first time with state loading if available.
    // Whether the state exists or not, we generate the missing embeddings:
    if (SemanticSearchSettings.getInstance().enabledInFilesTab) {
      FileEmbeddingsStorage.getInstance(project).prepareForSearch()
    }
  }
}

class IndexableFile(file: VirtualFile) : IndexableEntity {
  override val id = file.name.intern()
  override val indexableRepresentation by lazy { splitIdentifierIntoTokens(file.nameWithoutExtension).joinToString(separator = " ") }
}