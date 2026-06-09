// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi.symbol.identifier

import com.intellij.mermaid.lang.psi.MermaidNamedPsiElement
import com.intellij.mermaid.lang.psi.symbol.identifier.UnresolvedIdentifierSymbol.Companion.isDeclaration
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

@Suppress("UnstableApiUsage")
class IdentifierSymbolDeclarationProvider : PsiSymbolDeclarationProvider {
  override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
    if (element is MermaidNamedPsiElement && element.isDeclaration) {
      val symbol = IdentifierSymbol.createPointer(element).dereference() ?: return emptyList()
      return listOf(IdentifierSymbolDeclaration(element, symbol))
    }
    return emptyList()
  }

  private class IdentifierSymbolDeclaration(
    private val element: MermaidNamedPsiElement,
    private val symbol: IdentifierSymbol
  ) : PsiSymbolDeclaration {
    override fun getDeclaringElement(): PsiElement {
      return element
    }

    override fun getRangeInDeclaringElement(): TextRange {
      return TextRange(0, element.textLength)
    }

    override fun getSymbol(): Symbol {
      return symbol
    }
  }
}
