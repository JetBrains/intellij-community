// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ImportLocationHelper {

  fun getSearchStartPosition(anchor: PsiElement?, insertParent: PsiElement): PsiElement?

  companion object {
    @JvmStatic
    fun getInstance(): ImportLocationHelper {
      return ApplicationManager.getApplication().getService(ImportLocationHelper::class.java)
    }
  }
}

@ApiStatus.Internal
class PyImportLocationHelper : ImportLocationHelper {
  override fun getSearchStartPosition(anchor: PsiElement?, insertParent: PsiElement): PsiElement? {
    return insertParent.firstChild
  }
}
