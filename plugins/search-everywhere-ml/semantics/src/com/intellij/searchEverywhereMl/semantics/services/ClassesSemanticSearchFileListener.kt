package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings

@Service(Service.Level.PROJECT)
class ClassesSemanticSearchFileListener(project: Project) : SemanticSearchFileContentChangeListener<IndexableClass>(project) {
  override val isEnabled: Boolean
    get() = SemanticSearchSettings.getInstance().enabledInClassesTab

  override fun getStorage() = ClassEmbeddingsStorage.getInstance(project)

  override fun getEntity(id: String) = IndexableClass(id.intern())

  companion object {
    fun getInstance(project: Project): ClassesSemanticSearchFileListener = project.service()
  }
}