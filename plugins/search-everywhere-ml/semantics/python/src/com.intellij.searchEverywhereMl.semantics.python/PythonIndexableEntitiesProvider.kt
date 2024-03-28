package com.intellij.searchEverywhereMl.semantics.python

import com.intellij.platform.ml.embeddings.search.indices.FileIndexableEntitiesProvider
import com.intellij.platform.ml.embeddings.search.services.IndexableClass
import com.intellij.platform.ml.embeddings.search.services.IndexableSymbol
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction

class PythonIndexableEntitiesProvider : FileIndexableEntitiesProvider {
  override fun extractIndexableSymbols(file: PsiFile): List<IndexableSymbol> {
    return when (file) {
      is PyFile -> PsiTreeUtil.findChildrenOfAnyType(file, false, PyFunction::class.java)
        .filter { it.name != ANONYMOUS_ID }
        .map { IndexableSymbol(it.name?.intern() ?: "") }
      else -> emptyList()
    }
  }

  override fun extractIndexableClasses(file: PsiFile): List<IndexableClass> {
    return when (file) {
      is PyFile -> PsiTreeUtil.getStubChildrenOfTypeAsList(file, PyClass::class.java)
        .filter { it.name != ANONYMOUS_ID }
        .map { IndexableClass(it.name?.intern() ?: "") }
      else -> emptyList()
    }
  }

  companion object {
    private const val ANONYMOUS_ID = "<anonymous>"
  }
}