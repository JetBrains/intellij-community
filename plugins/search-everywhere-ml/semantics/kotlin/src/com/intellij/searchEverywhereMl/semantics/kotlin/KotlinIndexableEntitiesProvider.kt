package com.intellij.searchEverywhereMl.semantics.kotlin

import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.platform.ml.embeddings.indexer.FileIndexableEntitiesProvider
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableClass
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableSymbol
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
      .map { IndexableSymbol(EntityId(it.name?.intern() ?: "")) }
  }

  override fun extractIndexableClasses(file: PsiFile): List<IndexableClass> {
    return PsiTreeUtil.getStubChildrenOfTypeAsList(file, KtClass::class.java)
      .filter { it.name != ANONYMOUS_ID }
      .map { IndexableClass(EntityId(it.name?.intern() ?: "")) }
  }

  companion object {
    private const val ANONYMOUS_ID = "<anonymous>"
  }
}