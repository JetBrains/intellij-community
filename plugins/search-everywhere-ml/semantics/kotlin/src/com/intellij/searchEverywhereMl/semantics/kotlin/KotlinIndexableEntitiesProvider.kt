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
  override fun isEnabled(file: PsiFile) = file is KtFile

  override fun extractIndexableSymbols(file: PsiFile): List<IndexableSymbol> {
    return PsiTreeUtil.findChildrenOfAnyType(file, false, KtFunction::class.java)
      .filter { it.name != ANONYMOUS_ID }
      .map { IndexableSymbol(it.name?.intern() ?: "") }
  }

  override fun extractIndexableClasses(file: PsiFile): List<IndexableClass> {
    return PsiTreeUtil.getStubChildrenOfTypeAsList(file, KtClass::class.java)
      .filter { it.name != ANONYMOUS_ID }
      .map { IndexableClass(it.name?.intern() ?: "") }
  }

  companion object {
    private const val ANONYMOUS_ID = "<anonymous>"
  }
}