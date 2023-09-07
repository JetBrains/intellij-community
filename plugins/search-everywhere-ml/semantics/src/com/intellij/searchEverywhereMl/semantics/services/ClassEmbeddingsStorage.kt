package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.searchEverywhereMl.semantics.SemanticSearchBundle
import com.intellij.searchEverywhereMl.semantics.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.searchEverywhereMl.semantics.indices.FileIndexableEntitiesProvider
import com.intellij.searchEverywhereMl.semantics.indices.IndexableEntity
import com.intellij.searchEverywhereMl.semantics.services.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings
import com.intellij.searchEverywhereMl.semantics.utils.splitIdentifierIntoTokens
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.io.File

/**
 * Thread-safe service for semantic classes search.
 * Supports Java Kotlin classes.
 * Holds a state with embeddings for each available indexable item and persists it on disk after calculation.
 * Generates the embeddings for classes not present in the loaded state at the IDE startup event if semantic classes search is enabled.
 */
@Service(Service.Level.PROJECT)
class ClassEmbeddingsStorage(project: Project) : FileContentBasedEmbeddingsStorage<IndexableClass>(project) {
  override val index = DiskSynchronizedEmbeddingSearchIndex(
    project.getProjectCachePath(
      File(SEMANTIC_SEARCH_RESOURCES_DIR)
        .resolve(LocalArtifactsManager.getInstance().getModelVersion())
        .resolve(INDEX_DIR).toString()
    )
  )
  override val indexingTaskManager = EmbeddingIndexingTaskManager(index)

  override val scanningTitle = SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.classes.scanning.label")
  override val setupTitle = SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.classes.generation.label")
  override val spanIndexName = "semanticClasses"

  override val indexMemoryWeight: Int = 1
  override val indexStrongLimit = Registry.intValue("search.everywhere.ml.semantic.indexing.indexable.classes.limit")

  init {
    project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, ClassesSemanticSearchFileChangeListener.getInstance(project))
  }

  override fun checkSearchEnabled() = SemanticSearchSettings.getInstance().enabledInClassesTab

  @RequiresBackgroundThread
  override fun getIndexableEntities() = collectEntities(ClassesSemanticSearchFileChangeListener.getInstance(project))

  override fun traversePsiFile(file: PsiFile) = FileIndexableEntitiesProvider.extractClasses(file)

  companion object {
    private const val INDEX_DIR = "classes"

    fun getInstance(project: Project) = project.service<ClassEmbeddingsStorage>()
  }
}

@Suppress("unused")  // Registered in the plugin's XML file
class ClassSemanticSearchServiceInitializer : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (SemanticSearchSettings.getInstance().enabledInClassesTab) {
      ClassEmbeddingsStorage.getInstance(project).prepareForSearch()
    }
  }
}

open class IndexableClass(override val id: String) : IndexableEntity {
  override val indexableRepresentation: String by lazy { splitIdentifierIntoTokens(id).joinToString(separator = " ") }
}