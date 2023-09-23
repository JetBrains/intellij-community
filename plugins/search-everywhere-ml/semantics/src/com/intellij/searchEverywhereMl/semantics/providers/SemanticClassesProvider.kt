package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.semantics.services.ClassEmbeddingsStorage

class SemanticClassesProvider(private val project: Project, override var model: FilteringGotoByModel<*>) : SemanticPsiItemsProvider {
  override fun getEmbeddingsStorage() = ClassEmbeddingsStorage.getInstance(project)
}