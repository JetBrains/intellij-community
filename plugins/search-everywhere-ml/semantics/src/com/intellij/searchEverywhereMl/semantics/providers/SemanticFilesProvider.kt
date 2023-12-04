package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.search.services.FileEmbeddingsStorage
import com.intellij.platform.ml.embeddings.search.settings.SemanticSearchSettings

class SemanticFilesProvider(private val project: Project, override var model: FilteringGotoByModel<*>) : SemanticPsiItemsProvider {
  override fun getEmbeddingsStorage() = FileEmbeddingsStorage.getInstance(project)
  override fun isEnabled(): Boolean {
    return SemanticSearchSettings.getInstance().enabledInFilesTab
  }
}