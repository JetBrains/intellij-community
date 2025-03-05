package org.intellij.plugins.markdown.model.psi.headers.html

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
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
import org.intellij.plugins.markdown.model.psi.headers.HeaderAnchorLinkDestinationReference

/**
 * Needed only for resolving of [HtmlAnchorSymbol].
 * Searches only for [HeaderAnchorLinkDestinationReference].
 */
internal class MarkdownHtmlAnchorPsiReferenceSearcher
  //: PsiSymbolReferenceSearcher
{

  //override fun collectSearchRequests(parameters: PsiSymbolReferenceSearchParameters): Collection<Query<out PsiSymbolReference>> {
  //  val symbol = parameters.symbol as? HtmlAnchorSymbol ?: return emptyList()
  //  val project = parameters.project
  //  val searchScope = parameters.searchScope
  //  val request = buildSearchRequest(project, symbol, searchScope)
  //  return listOf(request)
  //}

  companion object {
    fun buildSearchRequest(project: Project, symbol: HtmlAnchorSymbol, searchScope: SearchScope): Query<out PsiSymbolReference> {
      val symbolPointer = symbol.createPointer()
      return SearchService.getInstance()
        .searchWord(project, symbol.searchText)
        .caseSensitive(false)
        .inContexts(SearchContext.inCodeHosts(), SearchContext.inCode(), SearchContext.inPlainText(), SearchContext.inStrings())
        .inScope(searchScope)
        .buildQuery(LeafOccurrenceMapper.withPointer(symbolPointer, Companion::findReferences))
    }

    private fun findReferences(symbol: Symbol, occurrence: LeafOccurrence): Collection<PsiSymbolReference> {
      val service = PsiSymbolReferenceService.getService()
      val (scope, psiElement, offset) = occurrence
      val elements = walkUp(psiElement, offset, scope).asSequence().filter { (element, _) -> element is PsiExternalReferenceHost }
      for ((element, offsetInElement) in elements) {
        val allFoundReferences = service.getReferences(element, PsiSymbolReferenceHints.offsetHint(offsetInElement)).asSequence()
        val foundReferences = allFoundReferences.filterIsInstance<HeaderAnchorLinkDestinationReference>()
        val relevantReferences = foundReferences.filter { it.rangeInElement.containsOffset(offsetInElement) }
        val resolvedReferences = relevantReferences.filter { it.resolvesTo(symbol) }
        if (resolvedReferences.any()) {
          return resolvedReferences.toList()
        }
      }
      return emptyList()
    }
  }
}
