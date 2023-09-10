package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings

@Service(Service.Level.PROJECT)
class SymbolsSemanticSearchFileChangeListener(project: Project)
  : BulkFileListener, SemanticSearchFileContentChangeListener<IndexableSymbol>(project) {
  override fun getStorage() = SymbolEmbeddingStorage.getInstance(project)

  override fun getEntity(id: String) = IndexableSymbol(id.intern())

  override fun after(events: List<VFileEvent>) {
    if (!SemanticSearchSettings.getInstance().enabledInSymbolsTab) return
    processEvents(events)
  }

  companion object {
    fun getInstance(project: Project) = project.service<SymbolsSemanticSearchFileChangeListener>()
  }
}