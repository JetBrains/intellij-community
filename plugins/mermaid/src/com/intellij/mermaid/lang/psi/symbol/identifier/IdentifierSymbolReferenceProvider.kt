// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi.symbol.identifier

import com.intellij.mermaid.lang.psi.MermaidNamedPsiElement
import com.intellij.mermaid.lang.psi.symbol.identifier.UnresolvedIdentifierSymbol.Companion.isDeclaration
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange

@Suppress("UnstableApiUsage")
class IdentifierSymbolReferenceProvider : PsiSymbolReferenceProvider {
  override fun getReferences(
    element: PsiExternalReferenceHost,
    hints: PsiSymbolReferenceHints
  ): Collection<PsiSymbolReference> {
    if (element !is MermaidNamedPsiElement || element.isDeclaration) {
      return emptyList()
    }
    val rangeInElement = TextRange(0, element.textLength)
    val text = element.text
    val reference = IdentifierSymbolReference(element, rangeInElement, text)
    return listOf(reference)
  }

  override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> {
    return emptyList()
  }
}
