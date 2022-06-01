package com.github.firsttimeinforever.mermaid.lang.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase


class MermaidReference(
  element: PsiElement,
  textRange: TextRange,
  private val key: String = element.text
) : PsiReferenceBase<PsiElement>(element, textRange) {

  override fun resolve(): PsiElement? {
    var result: PsiElement? = null
    var process = true
    element.containingFile.acceptChildren(object : MermaidVisitor() {
      override fun visitPsiElement(o: PsiElement) {
        if (process) {
          o.acceptChildren(this)
        }
      }

      override fun visitNamedElement(o: MermaidNamedElement) {
        if (process && o.name.equals(key)) {
          result = o
          process = false
        }
      }
    })
    return result
  }

  override fun getVariants(): Array<Any?> {
    val variants = mutableListOf<MermaidNamedElement>()
    element.containingFile.acceptChildren(object : MermaidVisitor() {
      override fun visitPsiElement(o: PsiElement) {
        o.acceptChildren(this)
      }

      override fun visitNamedElement(o: MermaidNamedElement) {
        variants.add(o)
      }
    })
    return variants.toTypedArray()
  }
}
