package com.intellij.searchEverywhereMl.semantics.kotlin

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.searchEverywhereMl.semantics.indices.FileIndexableEntitiesProvider
import com.intellij.searchEverywhereMl.semantics.services.IndexableClass
import com.intellij.searchEverywhereMl.semantics.services.IndexableSymbol
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