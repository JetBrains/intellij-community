package com.intellij.searchEverywhereMl.semantics.python

import com.intellij.platform.ml.embeddings.indexer.SymbolsProvider
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableSymbol
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction

internal class PythonSymbolsProvider : SymbolsProvider {
  override fun extract(file: PsiFile): List<IndexableSymbol> {
    return PsiTreeUtil.findChildrenOfAnyType(file, false, PyFunction::class.java).asSequence()
      .mapNotNull { f -> f.name }
      .filter { name -> name != ANONYMOUS_ID }
      .map { name -> IndexableSymbol(EntityId(name)) }
      .toList()
  }
}

private const val ANONYMOUS_ID = "<anonymous>"
