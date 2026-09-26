// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.psi.symbol.identifier

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.mermaid.lang.psi.MermaidNamedPsiElement
import com.intellij.mermaid.lang.psi.symbol.MermaidPsiSymbolReference
import com.intellij.mermaid.lang.psi.symbol.MermaidPsiUsage
import com.intellij.mermaid.lang.psi.symbol.MermaidSymbol
import com.intellij.mermaid.lang.psi.symbol.identifier.UnresolvedIdentifierSymbol.Companion.isDeclaration
import com.intellij.model.Pointer
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.walkUp
import com.intellij.util.AbstractQuery
import com.intellij.util.Processor
import com.intellij.util.Query

@Suppress("UnstableApiUsage")
class IdentifierSymbolUsageSearcher : UsageSearcher {
  override fun collectSearchRequests(parameters: UsageSearchParameters): Collection<Query<out Usage>> {
    val target = parameters.target
    if (target !is MermaidSymbol) {
      return emptyList()
    }
    val project = parameters.project
    val searchText = target.searchText.takeIf { it.isNotEmpty() } ?: return emptyList()
    val usages = buildSearchRequest(project, target, searchText, parameters.searchScope)
    val otherDeclarations = buildDeclarationsSearchRequest(project, target, searchText, parameters.searchScope)
    val selfUsage = buildDirectTargetQuery(MermaidPsiUsage.create(target.file, target.range, declaration = true))
    return listOf(usages, otherDeclarations, selfUsage)
  }

  companion object {
    fun buildSearchRequest(
      project: Project,
      target: MermaidSymbol,
      searchText: String,
      searchScope: SearchScope
    ): Query<out PsiUsage> {
      val symbolPointer = Pointer.hardPointer(target)
      return SearchService.getInstance()
        .searchWord(project, searchText)
        .caseSensitive(true)
        .inContexts(SearchContext.IN_CODE)
        .inScope(searchScope)
        .inFilesWithLanguage(MermaidLanguage)
        .buildQuery(LeafOccurrenceMapper.withPointer(symbolPointer, Companion::findReferencesToSymbol))
        .mapping { MermaidPsiUsage.create(it) }
    }

    fun buildDeclarationsSearchRequest(
      project: Project,
      target: MermaidSymbol,
      searchText: String,
      searchScope: SearchScope
    ): Query<out PsiUsage> {
      val symbolPointer = Pointer.hardPointer(target)
      return SearchService.getInstance()
        .searchWord(project, searchText)
        .caseSensitive(true)
        .inContexts(SearchContext.IN_CODE)
        .inScope(searchScope)
        .inFilesWithLanguage(MermaidLanguage)
        .buildQuery(LeafOccurrenceMapper.withPointer(symbolPointer, Companion::findOtherDeclarationsOfSymbol))
        .mapping { MermaidPsiUsage.create(it, it.textRangeInParent, true) }
    }

    /**
     * Creates a query directly resolving to [usage].
     */
    fun buildDirectTargetQuery(usage: PsiUsage): Query<PsiUsage> {
      return MermaidDirectUsageQuery(usage)
    }

    private class MermaidDirectUsageQuery(private val usage: PsiUsage) : AbstractQuery<PsiUsage>() {
      override fun processResults(consumer: Processor<in PsiUsage>): Boolean {
        return runReadAction {
          consumer.process(usage)
        }
      }
    }

    private fun findReferencesToSymbol(
      symbol: MermaidSymbol,
      leafOccurrence: LeafOccurrence
    ): Collection<MermaidPsiSymbolReference> {
      val service = PsiSymbolReferenceService.getService()
      val (scope, psiElement, offset) = leafOccurrence
      val elements =
        walkUp(psiElement, offset, scope).asSequence().filter { (element, _) -> element is PsiExternalReferenceHost }
      for ((element, offsetInElement) in elements) {
        val allFoundReferences =
          service.getReferences(element, PsiSymbolReferenceHints.offsetHint(offsetInElement)).asSequence()
        val foundReferences = allFoundReferences.filterIsInstance<IdentifierSymbolReference>()
          .filter { it.rangeInElement.containsOffset(offsetInElement) }
          .filter { it.resolvesTo(symbol) }
        if (foundReferences.any()) {
          return foundReferences.toList()
        }
      }
      return emptyList()
    }

    private fun findOtherDeclarationsOfSymbol(
      symbol: MermaidSymbol,
      leafOccurrence: LeafOccurrence
    ): Collection<MermaidNamedPsiElement> {
      val (scope, psiElement, offset) = leafOccurrence
      val elements =
        walkUp(psiElement, offset, scope).asSequence().filter { (element, _) -> element is PsiExternalReferenceHost }
      for ((element, offsetInElement) in elements) {
        if (element !is MermaidNamedPsiElement) {
          continue
        }
        val foundReferences = sequenceOf(element)
          .filter { it.textRangeInParent.containsOffset(offsetInElement) }
          .filter { it.isDeclaration }
          .filter { it.textMatches(symbol.searchText) }
          .filter { it.textRange != symbol.range }
        if (foundReferences.any()) {
          return foundReferences.toList()
        }
      }
      return emptyList()
    }
  }
}
