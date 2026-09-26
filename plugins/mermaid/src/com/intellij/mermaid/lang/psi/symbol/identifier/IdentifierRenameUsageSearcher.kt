// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi.symbol.identifier

import com.intellij.find.usages.api.PsiUsage
import com.intellij.mermaid.lang.psi.symbol.MermaidPsiUsage
import com.intellij.mermaid.lang.psi.symbol.identifier.IdentifierSymbolUsageSearcher.Companion.buildDeclarationsSearchRequest
import com.intellij.mermaid.lang.psi.symbol.identifier.IdentifierSymbolUsageSearcher.Companion.buildSearchRequest
import com.intellij.refactoring.rename.api.PsiModifiableRenameUsage
import com.intellij.refactoring.rename.api.RenameUsage
import com.intellij.refactoring.rename.api.RenameUsageSearchParameters
import com.intellij.refactoring.rename.api.RenameUsageSearcher
import com.intellij.util.Query

@Suppress("UnstableApiUsage")
class IdentifierRenameUsageSearcher: RenameUsageSearcher {
  override fun collectSearchRequests(parameters: RenameUsageSearchParameters): Collection<Query<out RenameUsage>> {
    val target = parameters.target
    if (target !is IdentifierRenameTarget) {
      return emptyList()
    }
    val symbol = target.symbol
    val project = parameters.project
    val searchScope = parameters.searchScope
    val searchText = symbol.searchText.takeIf { it.isNotEmpty() } ?: return emptyList()
    val usages = buildSearchRequest(project, symbol, searchText, searchScope)
    val otherDeclarations = buildDeclarationsSearchRequest(project, symbol, searchText, searchScope)
    val queries = buildList {
      add(usages)
      add(otherDeclarations)
      // symbol is not UnresolvedIdentifierSymbol
      if (symbol is IdentifierSymbol) {
        add(createSelfUsageQuery(symbol))
      }
    }
    return queries.map { it.mapping(PsiModifiableRenameUsage::defaultPsiModifiableRenameUsage) }
  }

  private fun createSelfUsageQuery(symbol: MermaidIdentifierSymbol): Query<PsiUsage> {
    val selfUsage = MermaidPsiUsage.create(
      symbol.file,
      symbol.range,
      declaration = true
    )
    return IdentifierSymbolUsageSearcher.buildDirectTargetQuery(selfUsage)
  }
}
