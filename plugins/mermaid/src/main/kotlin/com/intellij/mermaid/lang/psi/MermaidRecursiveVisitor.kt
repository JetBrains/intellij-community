package com.intellij.mermaid.lang.psi

import com.intellij.psi.PsiElement

open class MermaidRecursiveVisitor : MermaidVisitor() {
  override fun visitPsiElement(element: PsiElement) {
    super.visitPsiElement(element)
    element.acceptChildren(this)
  }
}
