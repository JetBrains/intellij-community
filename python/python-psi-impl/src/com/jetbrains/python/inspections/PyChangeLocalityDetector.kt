// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInsight.daemon.ChangeLocalityDetector
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.python.psi.PyFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyChangeLocalityDetector : ChangeLocalityDetector {
  override fun getChangeHighlightingDirtyScopeFor(changedElement: PsiElement): PsiElement? {
    if (changedElement is PsiWhiteSpace) {
      val parent = changedElement.parent
      if (parent is PyFile) {
        return parent
      }
    }
    return null
  }
}