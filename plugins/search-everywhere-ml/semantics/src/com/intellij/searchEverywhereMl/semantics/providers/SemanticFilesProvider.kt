package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.semantics.services.FileEmbeddingsStorage

class SemanticFilesProvider(private val project: Project) : SemanticPsiItemsProvider {
  override val model = GotoFileModel(project)

  override fun getEmbeddingsStorage() = FileEmbeddingsStorage.getInstance(project)
}