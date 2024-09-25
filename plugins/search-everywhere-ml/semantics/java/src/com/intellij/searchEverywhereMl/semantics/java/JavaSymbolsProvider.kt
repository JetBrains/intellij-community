package com.intellij.searchEverywhereMl.semantics.java

import com.intellij.platform.ml.embeddings.indexer.SymbolsProvider
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableSymbol
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile

internal class JavaSymbolsProvider : SymbolsProvider {
  override fun extract(file: PsiFile): List<IndexableSymbol> {
    return (file as PsiJavaFile).classes.filterNotNull()
      .flatMap { it.methods.toList() }
      .filter { it.name != ANONYMOUS_ID }
      .map { IndexableSymbol(EntityId(it.name.intern())) }
  }

  companion object {
    private const val ANONYMOUS_ID = "<anonymous>"
  }
}
