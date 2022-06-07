package com.github.firsttimeinforever.mermaid.lang.psi

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement

class MermaidRefactoringSupportProvider: RefactoringSupportProvider() {
  override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
    return element is MermaidNamedElement
  }
}
