package com.github.firsttimeinforever.mermaid.lang.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase


class MermaidReference(
  element: PsiElement,
  textRange: TextRange,
  private val key: String = element.text.trim()
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
          if (isFirstRef(o)) {
            result = o
            process = false
          } else if (result == null) {
            result = o
          }
        }
      }
    })
    return result
  }

  override fun getVariants(): Array<Any?> {
    val variantsMap = mutableMapOf<String, MutableList<MermaidNamedElement>>()
    element.containingFile.acceptChildren(object : MermaidVisitor() {
      override fun visitPsiElement(o: PsiElement) {
        o.acceptChildren(this)
      }

      override fun visitNamedElement(o: MermaidNamedElement) {
        if (isFirstRef(o) || !variantsMap.contains(o.name!!)) {
          variantsMap[o.name!!] = mutableListOf(o)
        }
      }
    })
    return variantsMap.values.flatten().toTypedArray()
  }

  private fun isFirstRef(o: MermaidNamedElement?): Boolean {
    return o != null
      && (o.parent is MermaidSubgraphStatement
      || o.parent is MermaidActorStatement
      || o.parent is MermaidClassStatement
      || (o.parent is MermaidStateId && o.parent.parent is MermaidStateDeclaration)
      || o.parent is MermaidEntityDeclaration
      || o.parent is MermaidRequirementDef
      || o.parent is MermaidBranchStatement
      )
  }
}
