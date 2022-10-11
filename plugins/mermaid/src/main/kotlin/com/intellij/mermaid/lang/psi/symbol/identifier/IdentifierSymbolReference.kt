package com.intellij.mermaid.lang.psi.symbol.identifier

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.mermaid.lang.psi.MermaidNamedPsiElement
import com.intellij.mermaid.lang.psi.MermaidVisitor
import com.intellij.mermaid.lang.psi.symbol.MermaidPsiSymbolReferenceBase
import com.intellij.mermaid.lang.psi.symbol.identifier.UnresolvedIdentifierSymbol.Companion.isDeclaration
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiCompletableReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

@Suppress("UnstableApiUsage")
class IdentifierSymbolReference(
  element: PsiElement,
  rangeInElement: TextRange,
  private val text: String,
) : MermaidPsiSymbolReferenceBase(element, rangeInElement), PsiCompletableReference {
  override fun resolveReference(): Collection<Symbol> {
    val file = element.containingFile
    val declarations = file.collectNamedElements().asSequence().filter { it.isDeclaration }
    val matchingDeclarations = declarations.filter { it.text == text }
    if (!matchingDeclarations.iterator().hasNext()) {
      return listOf(element).mapNotNull {
        (it as? MermaidNamedPsiElement)?.let { element ->
          UnresolvedIdentifierSymbol.createPointer(
            element
          ).dereference()
        }
      }
    }
    val symbols = matchingDeclarations.mapNotNull { IdentifierSymbol.createPointer(it).dereference() }
    return symbols.toList()
  }

  override fun getCompletionVariants(): Collection<LookupElement> {
    val file = element.containingFile
    val labels = file.collectNamedElements().asSequence()
    return labels.map { LookupElementBuilder.create(it.name) }.toList()
  }

  companion object {
    private fun PsiFile.collectNamedElements(): List<MermaidNamedPsiElement> {
      val elements = arrayListOf<MermaidNamedPsiElement>()
      val visitor = object : MermaidVisitor() {
        override fun visitElement(element: PsiElement) {
          if (element is MermaidNamedPsiElement) {
            elements.add(element)
          }
          super.visitElement(element)
          element.acceptChildren(this)
        }
      }
      accept(visitor)
      return elements
    }
  }
}
