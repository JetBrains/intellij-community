// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.dependencies

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

internal fun resolvePsiFile(injectedLanguageManager: InjectedLanguageManager, psiElement: PsiElement): ResolvedPsiFile =
  when {
    psiElement is PsiFile -> ResolvedPsiFile.File(psiElement)
    else -> when (val injectedElement = injectedLanguageManager.getInjectedPsiFiles(psiElement)?.firstOrNull()?.first) {
      is PsiFile -> ResolvedPsiFile.InjectedFile(injectedElement)
      else -> ResolvedPsiFile.NonFile
    }
  }

internal sealed interface ResolvedPsiFile {
  data class File(val file: PsiFile) : ResolvedPsiFile
  data class InjectedFile(val file: PsiFile) : ResolvedPsiFile
  data object NonFile : ResolvedPsiFile

  val isInjected: Boolean
    get() =
      when (this) {
        is File -> false
        is InjectedFile -> true
        NonFile -> false
      }
}
