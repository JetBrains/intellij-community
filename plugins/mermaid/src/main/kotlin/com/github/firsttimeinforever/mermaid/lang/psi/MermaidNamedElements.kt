package com.github.firsttimeinforever.mermaid.lang.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiReference

interface MermaidNamedElement : PsiNameIdentifierOwner

abstract class MermaidNamedPsiElement(node: ASTNode) : ASTWrapperPsiElement(node), MermaidNamedElement {
  override fun getNameIdentifier(): PsiElement {
    return this
  }

  override fun getName(): String {
    return text.trim()
  }

  override fun setName(name: String): MermaidNamedPsiElement? {
    val manipulator = ElementManipulators.getManipulator(this)
    if (manipulator != null) {
      return manipulator.handleContentChange(this, name)
    }
    return this
  }

  override fun getTextOffset(): Int {
    return if (nameIdentifier == this) {
      super.getTextOffset()
    } else {
      nameIdentifier.textOffset
    }
  }

  override fun getReference(): PsiReference? {
    return MermaidReference(this, TextRange(0, name.length))
  }
}
