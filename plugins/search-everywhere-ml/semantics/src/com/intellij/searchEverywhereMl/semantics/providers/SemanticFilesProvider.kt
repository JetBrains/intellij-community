package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.semantics.services.FileEmbeddingsStorage

class SemanticFilesProvider(private val project: Project, override var model: FilteringGotoByModel<*>) : SemanticPsiItemsProvider {
  override fun getEmbeddingsStorage() = FileEmbeddingsStorage.getInstance(project)
}