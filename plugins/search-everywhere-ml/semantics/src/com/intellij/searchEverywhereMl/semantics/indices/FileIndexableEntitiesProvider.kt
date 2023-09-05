package com.intellij.searchEverywhereMl.semantics.indices

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import com.intellij.searchEverywhereMl.semantics.services.IndexableClass
import com.intellij.searchEverywhereMl.semantics.services.IndexableSymbol

interface FileIndexableEntitiesProvider {
  fun extractIndexableSymbols(file: PsiFile): List<IndexableSymbol>

  fun extractIndexableClasses(file: PsiFile): List<IndexableClass>

  companion object {
    private val EP_NAME: ExtensionPointName<FileIndexableEntitiesProvider> =
      ExtensionPointName.create("com.intellij.searcheverywhere.ml.fileIndexableEntitiesProvider")

    fun extractSymbols(file: PsiFile): List<IndexableSymbol> {
      for (extension in EP_NAME.extensionList) {
        val symbols = extension.extractIndexableSymbols(file)
        if (symbols.isNotEmpty()) return symbols
      }
      return emptyList()
    }

    fun extractClasses(file: PsiFile): List<IndexableClass> {
      for (extension in EP_NAME.extensionList) {
        val classes = extension.extractIndexableClasses(file)
        if (classes.isNotEmpty()) return classes
      }
      return emptyList()
    }
  }
}