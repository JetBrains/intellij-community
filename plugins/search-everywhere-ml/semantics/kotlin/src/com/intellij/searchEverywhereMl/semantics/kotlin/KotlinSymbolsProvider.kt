package com.intellij.searchEverywhereMl.semantics.kotlin

import com.intellij.platform.ml.embeddings.indexer.SymbolsProvider
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableSymbol
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileContent
import org.jetbrains.kotlin.psi.KtFunction

internal class KotlinSymbolsProvider : SymbolsProvider {
  override fun extract(fileContent: FileContent): List<IndexableSymbol> {
    return PsiTreeUtil.findChildrenOfAnyType(fileContent.psiFile, false, KtFunction::class.java).asSequence()
      .mapNotNull { f -> f.name }
      .filter { name -> name != ANONYMOUS_ID }
      .map { name -> IndexableSymbol(EntityId(name)) }
      .toList()
  }
}

private const val ANONYMOUS_ID = "<anonymous>"
