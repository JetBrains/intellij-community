package com.intellij.mermaid.lang.psi

import com.intellij.psi.PsiElement

open class MermaidRecursiveVisitor : MermaidVisitor() {
  override fun visitElement(element: PsiElement) {
    super.visitElement(element)
    element.acceptChildren(this)
  }
}
