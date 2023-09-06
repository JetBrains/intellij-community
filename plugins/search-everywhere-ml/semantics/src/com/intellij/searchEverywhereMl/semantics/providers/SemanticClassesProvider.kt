package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.util.gotoByName.GotoClassModel2
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.semantics.services.ClassEmbeddingsStorage

class SemanticClassesProvider(private val project: Project) : SemanticPsiItemsProvider {
  override val model = GotoClassModel2(project)

  override fun getEmbeddingsStorage() = ClassEmbeddingsStorage.getInstance(project)
}