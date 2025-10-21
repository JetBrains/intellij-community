package com.jetbrains.python.inspections

import com.intellij.codeInsight.daemon.ChangeLocalityDetector
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.python.psi.PyFile

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