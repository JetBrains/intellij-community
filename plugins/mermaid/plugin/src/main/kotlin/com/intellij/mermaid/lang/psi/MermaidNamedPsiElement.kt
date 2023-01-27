package com.intellij.mermaid.lang.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.psi.NavigatablePsiElement

@Suppress("UnstableApiUsage")
abstract class MermaidNamedPsiElement(
  node: ASTNode
): ASTWrapperPsiElement(node), MermaidPsiElement, NavigatablePsiElement, PsiExternalReferenceHost {
  override fun getName(): String {
    return text.trim()
  }
}
