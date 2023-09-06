package com.intellij.searchEverywhereMl.semantics.providers

import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.semantics.services.SymbolEmbeddingStorage

class SemanticSymbolsProvider(private val project: Project, parentDisposable: Disposable) : SemanticPsiItemsProvider {
  override val model = GotoSymbolModel2(project, parentDisposable)

  override fun getEmbeddingsStorage() = SymbolEmbeddingStorage.getInstance(project)
}