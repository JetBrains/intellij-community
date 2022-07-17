package org.intellij.plugins.markdown.model.psi

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.model.Pointer
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.LeafOccurrenceMapper
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.walkUp
import com.intellij.util.AbstractQuery
import com.intellij.util.Processor
import com.intellij.util.Query

internal class MarkdownSymbolUsageSearcher: UsageSearcher {
  override fun collectSearchRequests(parameters: UsageSearchParameters): Collection<Query<out Usage>> {
    val target = parameters.target
    if (target !is MarkdownSymbolWithUsages) {
      return emptyList()
    }
    val project = parameters.project
    val searchText = target.searchText.takeIf { it.isNotEmpty() } ?: return emptyList()
    val usages = buildSearchRequest(project, target, searchText, parameters.searchScope)
    val selfUsage = buildDirectTargetQuery(MarkdownPsiUsage.create(target.file, target.range, declaration = true))
    return listOf(usages, selfUsage)
  }

  companion object {
    fun buildSearchRequest(project: Project, target: MarkdownSymbol, searchText: String, searchScope: SearchScope): Query<out PsiUsage> {
      val symbolPointer = Pointer.hardPointer(target)
      return SearchService.getInstance()
        .searchWord(project, searchText)
        .caseSensitive(false)
        .inContexts(SearchContext.IN_CODE_HOSTS, SearchContext.IN_CODE, SearchContext.IN_PLAIN_TEXT, SearchContext.IN_STRINGS)
        .inScope(searchScope)
        .buildQuery(LeafOccurrenceMapper.withPointer(symbolPointer, Companion::findReferencesToSymbol))
        .mapping { MarkdownPsiUsage.create(it) }
    }

    /**
     * Creates a query directly resolving to [usage].
     */
    fun buildDirectTargetQuery(usage: PsiUsage): Query<PsiUsage> {
      return MarkdownDirectUsageQuery(usage)
    }

    private class MarkdownDirectUsageQuery(private val usage: PsiUsage): AbstractQuery<PsiUsage>() {
      override fun processResults(consumer: Processor<in PsiUsage>): Boolean {
        return runReadAction {
          consumer.process(usage)
        }
      }
    }

    private fun findReferencesToSymbol(symbol: MarkdownSymbol, leafOccurrence: LeafOccurrence): Collection<MarkdownPsiSymbolReference> {
      val service = service<PsiSymbolReferenceService>()
      val (scope, psiElement, offset) = leafOccurrence
      val elements = walkUp(psiElement, offset, scope).asSequence().filter { (element, _) -> element is PsiExternalReferenceHost }
      for ((element, offsetInElement) in elements) {
        val allFoundReferences = service.getReferences(element, PsiSymbolReferenceHints.offsetHint(offsetInElement)).asSequence()
        val foundReferences = allFoundReferences.filterIsInstance<MarkdownPsiSymbolReference>()
          .filter { it.rangeInElement.containsOffset(offsetInElement) }
          .filter { it.resolvesTo(symbol) }
        if (foundReferences.any()) {
          return foundReferences.toList()
        }
      }
      return emptyList()
    }
  }
}
