package com.intellij.searchEverywhereMl.semantics.kotlin

import com.intellij.platform.ml.embeddings.indexer.ClassesProvider
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableClass
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClass

internal class KotlinClassesProvider : ClassesProvider {
  override fun extract(file: PsiFile): List<IndexableClass> {
    return PsiTreeUtil.getStubChildrenOfTypeAsList(file, KtClass::class.java).asSequence()
      .map { c -> c.name }
      .filterNotNull()
      .filter { name -> name != ANONYMOUS_ID }
      .map { name -> IndexableClass(EntityId(name)) }
      .toList()
  }

  companion object {
    private const val ANONYMOUS_ID = "<anonymous>"
  }
}