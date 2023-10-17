package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings

@Service(Service.Level.PROJECT)
class SymbolsSemanticSearchFileListener(project: Project) : SemanticSearchFileContentChangeListener<IndexableSymbol>(project) {
  override val isEnabled: Boolean
    get() = SemanticSearchSettings.getInstance().enabledInSymbolsTab

  override fun getStorage() = SymbolEmbeddingStorage.getInstance(project)

  override fun getEntity(id: String) = IndexableSymbol(id.intern())

  companion object {
    fun getInstance(project: Project): SymbolsSemanticSearchFileListener = project.service()
  }
}