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
 * Thread-safe service for semantic symbols search.
 * Supports Java methods and Kotlin functions.
 * Holds a state with embeddings for each available indexable item and persists it on disk after calculation.
 * Generates the embeddings for symbols not present in the loaded state at the IDE startup event if semantic symbols search is enabled.
 */
@Service(Service.Level.PROJECT)
class SymbolEmbeddingStorage(project: Project) : FileContentBasedEmbeddingsStorage<IndexableSymbol>(project) {
  override val index = DiskSynchronizedEmbeddingSearchIndex(
    project.getProjectCachePath(
      File(SEMANTIC_SEARCH_RESOURCES_DIR)
        .resolve(LocalArtifactsManager.getInstance().getModelVersion())
        .resolve(INDEX_DIR).toString()
    )
  )
  override val indexingTaskManager = EmbeddingIndexingTaskManager(index)

  override val scanningTitle = SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.symbols.scanning.label")
  override val setupTitle = SemanticSearchBundle.getMessage("search.everywhere.ml.semantic.symbols.generation.label")
  override val spanIndexName = "semanticSymbols"

  override val indexMemoryWeight: Int = 2
  override val indexStrongLimit = Registry.intValue("search.everywhere.ml.semantic.indexing.indexable.symbols.limit")

  init {
    project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, SymbolsSemanticSearchFileChangeListener.getInstance(project))
  }

  override fun checkSearchEnabled() = SemanticSearchSettings.getInstance().enabledInSymbolsTab

  @RequiresBackgroundThread
  override fun getIndexableEntities() = collectEntities(SymbolsSemanticSearchFileChangeListener.getInstance(project))

  override fun traversePsiFile(file: PsiFile) = FileIndexableEntitiesProvider.extractSymbols(file)

  companion object {
    private const val INDEX_DIR = "symbols"

    fun getInstance(project: Project) = project.service<SymbolEmbeddingStorage>()
  }
}

@Suppress("unused")  // Registered in the plugin's XML file
class SymbolSemanticSearchServiceInitializer : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (SemanticSearchSettings.getInstance().enabledInSymbolsTab) {
      SymbolEmbeddingStorage.getInstance(project).prepareForSearch()
    }
  }
}

open class IndexableSymbol(override val id: String) : IndexableEntity {
  override val indexableRepresentation: String by lazy { splitIdentifierIntoTokens(id).joinToString(separator = " ") }
}