package com.intellij.ide.actions.searcheverywhere.ml.id

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.psi.PsiFileSystemItem

private class FileKeyProvider: ElementKeyForIdProvider() {
  override fun getKey(element: Any): Any? {
    return when (element) {
      is PsiFileSystemItem -> element.virtualFile
      is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> (element.item as? PsiFileSystemItem)?.virtualFile
      else -> null
    }
  }
}