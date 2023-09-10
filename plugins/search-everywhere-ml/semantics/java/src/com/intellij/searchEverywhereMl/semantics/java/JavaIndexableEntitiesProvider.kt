package com.intellij.searchEverywhereMl.semantics.java

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.searchEverywhereMl.semantics.indices.FileIndexableEntitiesProvider
import com.intellij.searchEverywhereMl.semantics.services.IndexableClass
import com.intellij.searchEverywhereMl.semantics.services.IndexableSymbol

class JavaIndexableEntitiesProvider : FileIndexableEntitiesProvider {
  override fun extractIndexableSymbols(file: PsiFile): List<IndexableSymbol> {
    return when (file) {
      is PsiJavaFile -> file.classes.filterNotNull()
        .flatMap { it.methods.toList() }
        .filter { it.name != ANONYMOUS_ID }
        .map { IndexableSymbol(it.name.intern()) }
      else -> emptyList()
    }
  }

  override fun extractIndexableClasses(file: PsiFile): List<IndexableClass> {
    return when (file) {
      is PsiJavaFile -> file.classes.filterNotNull()
        .filter { it.name != ANONYMOUS_ID }
        .map { IndexableClass(it.name?.intern() ?: "") }
      else -> emptyList()
    }
  }

  companion object {
    private const val ANONYMOUS_ID = "<anonymous>"
  }
}