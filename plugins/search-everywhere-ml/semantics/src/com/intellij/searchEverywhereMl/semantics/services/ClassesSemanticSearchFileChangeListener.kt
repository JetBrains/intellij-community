package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings

@Service(Service.Level.PROJECT)
class ClassesSemanticSearchFileChangeListener(project: Project)
  : BulkFileListener, SemanticSearchFileContentChangeListener<IndexableClass>(project) {
  override fun getStorage() = ClassEmbeddingsStorage.getInstance(project)

  override fun getEntity(id: String) = IndexableClass(id.intern())

  override fun after(events: List<VFileEvent>) {
    if (!SemanticSearchSettings.getInstance().enabledInClassesTab) return
    processEvents(events)
  }

  companion object {
    fun getInstance(project: Project) = project.service<ClassesSemanticSearchFileChangeListener>()
  }
}