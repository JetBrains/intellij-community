package com.intellij.searchEverywhereMl.semantics.python

import com.intellij.platform.ml.embeddings.indexer.ClassesProvider
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableClass
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass

internal class PythonClassesProvider : ClassesProvider {
  override fun extract(file: PsiFile): List<IndexableClass> {
    return PsiTreeUtil.getStubChildrenOfTypeAsList(file, PyClass::class.java)
      .filter { it.name != ANONYMOUS_ID }
      .map { IndexableClass(EntityId(it.name?.intern() ?: "")) }
  }

  companion object {
    private const val ANONYMOUS_ID = "<anonymous>"
  }
}