package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.search.services.ClassEmbeddingsStorage
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings

class SemanticClassesProvider(private val project: Project, override var model: FilteringGotoByModel<*>) : SemanticPsiItemsProvider {
  override fun getEmbeddingsStorage() = ClassEmbeddingsStorage.getInstance(project)

  override fun isEnabled(): Boolean = SearchEverywhereSemanticSettings.getInstance().enabledInClassesTab
}