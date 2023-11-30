package com.intellij.searchEverywhereMl.semantics.kotlin

import com.intellij.platform.ml.embeddings.search.indices.FileIndexableEntitiesProvider
import com.intellij.platform.ml.embeddings.search.services.IndexableClass
import com.intellij.platform.ml.embeddings.search.services.IndexableSymbol
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction

class KotlinIndexableEntitiesProvider : FileIndexableEntitiesProvider {
  override fun extractIndexableSymbols(file: PsiFile): List<IndexableSymbol> {
    return when (file) {
      is KtFile -> PsiTreeUtil.findChildrenOfAnyType(file, false, KtFunction::class.java)
        .filter { it.name != ANONYMOUS_ID }
        .map { IndexableSymbol(it.name?.intern() ?: "") }
      else -> emptyList()
    }
  }

  override fun extractIndexableClasses(file: PsiFile): List<IndexableClass> {
    return when (file) {
      is KtFile -> PsiTreeUtil.getStubChildrenOfTypeAsList(file, KtClass::class.java)
        .filter { it.name != ANONYMOUS_ID }
        .map { IndexableClass(it.name?.intern() ?: "") }
      else -> emptyList()
    }
  }

  companion object {
    private const val ANONYMOUS_ID = "<anonymous>"
  }
}