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
    var result: PsiElement = this
    var process = true
    containingFile.acceptChildren(object : MermaidVisitor() {
      override fun visitPsiElement(o: PsiElement) {
        if (process) {
          o.acceptChildren(this)
        }
      }

      override fun visitNamedElement(o: MermaidNamedElement) {
        if (process && o.name.equals(name)) {
          if (node.psi != o) {
            result = o
          }
          process = false
        }
      }
    })

    return result
  }

  override fun getName(): String {
    return text
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
