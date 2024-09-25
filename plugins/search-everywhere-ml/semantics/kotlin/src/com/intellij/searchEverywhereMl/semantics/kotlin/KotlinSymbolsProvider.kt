package com.intellij.searchEverywhereMl.semantics.kotlin

import com.intellij.platform.ml.embeddings.indexer.SymbolsProvider
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableSymbol
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFunction

internal class KotlinSymbolsProvider : SymbolsProvider {
  override fun extract(file: PsiFile): List<IndexableSymbol> {
    return PsiTreeUtil.findChildrenOfAnyType(file, false, KtFunction::class.java)
      .filter { it.name != ANONYMOUS_ID }
      .map { IndexableSymbol(EntityId(it.name?.intern() ?: "")) }
  }

  companion object {
    private const val ANONYMOUS_ID = "<anonymous>"
  }
}