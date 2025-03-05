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
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.walkUp
import com.intellij.util.Query
import org.intellij.plugins.markdown.model.psi.headers.MarkdownDirectUsageQuery
import org.intellij.plugins.markdown.model.psi.headers.html.findInjectedHtmlFile

internal class MarkdownSymbolUsageSearcher: UsageSearcher {
  override fun collectSearchRequests(parameters: UsageSearchParameters): Collection<Query<out Usage>> {
    val target = parameters.target
    if (target !is MarkdownSymbolWithUsages) {
      return emptyList()
    }
    return buildSearchRequests(parameters.project, parameters.searchScope, target)
  }

  companion object {
    private fun buildSearchRequests(project: Project, searchScope: SearchScope, target: MarkdownSymbolWithUsages): Collection<Query<out Usage>> {
      val searchText = target.searchText.takeIf { it.isNotEmpty() } ?: return emptyList()
      val usages = buildSearchRequest(project, target, searchText, searchScope)
      val selfUsage = MarkdownDirectUsageQuery(createSelfUsage(target))
      return listOf(usages, selfUsage)
    }

    private fun createSelfUsage(target: MarkdownSymbolWithUsages): PsiUsage {
      val file = target.file
      val actualFile = when (target) {
        is MarkdownSymbolInsideInjection -> findInjectedHtmlFile(file) ?: file
        else -> file
      }
      return MarkdownPsiUsage.create(actualFile, target.range, declaration = true)
    }

    fun buildSearchRequest(project: Project, target: MarkdownSymbol, searchText: String, searchScope: SearchScope): Query<out PsiUsage> {
      val symbolPointer = Pointer.hardPointer(target)
      return SearchService.getInstance()
        .searchWord(project, searchText)
        .caseSensitive(false)
        .inContexts(SearchContext.inCodeHosts(), SearchContext.inCode(), SearchContext.inPlainText(), SearchContext.inStrings())
        .inScope(searchScope)
        .buildQuery(LeafOccurrenceMapper.withPointer(symbolPointer, Companion::findReferencesToSymbol))
        .mapping { MarkdownPsiUsage.create(it) }
    }

    private fun findReferencesToSymbol(symbol: MarkdownSymbol, leafOccurrence: LeafOccurrence): Collection<MarkdownPsiSymbolReference> {
      val service = PsiSymbolReferenceService.getService()
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
