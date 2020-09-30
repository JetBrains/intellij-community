package com.jetbrains.python.codeInsight.imports

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement

interface ImportLocationHelper {

  fun getSearchStartPosition(anchor: PsiElement?, insertParent: PsiElement): PsiElement?

  companion object {
    @JvmStatic
    fun getInstance(): ImportLocationHelper {
      return ApplicationManager.getApplication().getService(ImportLocationHelper::class.java)
    }
  }
}

class PyImportLocationHelper : ImportLocationHelper {
  override fun getSearchStartPosition(anchor: PsiElement?, insertParent: PsiElement): PsiElement? {
    return insertParent.firstChild
  }
}
