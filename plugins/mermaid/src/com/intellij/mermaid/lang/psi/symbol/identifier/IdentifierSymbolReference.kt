// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi.symbol.identifier

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.mermaid.lang.psi.MermaidNamedPsiElement
import com.intellij.mermaid.lang.psi.symbol.MermaidPsiSymbolReferenceBase
import com.intellij.mermaid.lang.psi.symbol.identifier.UnresolvedIdentifierSymbol.Companion.isDeclaration
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiCompletableReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser

@Suppress("UnstableApiUsage")
class IdentifierSymbolReference(
  element: PsiElement,
  rangeInElement: TextRange,
  private val text: String,
) : MermaidPsiSymbolReferenceBase(element, rangeInElement), PsiCompletableReference {
  override fun resolveReference(): Collection<Symbol> {
    val file = element.containingFile
    val declarations = file.collectNamedElements().filter { it.isDeclaration }
    val matchingDeclarations = declarations.filter { it.text == text }
    if (matchingDeclarations.none()) {
      return listOf(element).mapNotNull {
        (it as? MermaidNamedPsiElement)?.let { element ->
          UnresolvedIdentifierSymbol.createPointer(
            element
          ).dereference()
        }
      }
    }
    return matchingDeclarations.mapNotNull { IdentifierSymbol.createPointer(it).dereference() }.toList()
  }

  override fun getCompletionVariants(): Collection<LookupElement> {
    val file = element.containingFile
    val labels = file.collectNamedElements()
    return labels.map { LookupElementBuilder.create(it.name) }.toList()
  }

  companion object {
    private fun PsiFile.collectNamedElements(): Sequence<MermaidNamedPsiElement> {
      return SyntaxTraverser.psiTraverser(this)
        .asSequence()
        .filterIsInstance<MermaidNamedPsiElement>()
    }
  }
}
