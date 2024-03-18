package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.openapi.util.Disposer
import com.intellij.searchEverywhereMl.SemanticSearchEverywhereContributor
import com.intellij.searchEverywhereMl.semantics.settings.SearchEverywhereSemanticSettings
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class SearchEverywhereMlContributorReplacementImpl : SearchEverywhereMlContributorReplacement {
  override fun replaceInSeparateTab(contributor: SearchEverywhereContributor<*>): SearchEverywhereContributor<*> {
    if (contributor is SemanticSearchEverywhereContributor) return contributor
    val initEvent = SearchEverywhereMlContributorReplacement.initEvent.get() ?: return contributor
    if (!SearchEverywhereSemanticSettings.getInstance().isEnabledInTab(contributor.searchProviderId)) return contributor
    return when (contributor.searchProviderId) {
      ActionSearchEverywhereContributor::class.java.simpleName -> configureContributor(
        SemanticActionSearchEverywhereContributor(contributor as ActionSearchEverywhereContributor), contributor)
      FileSearchEverywhereContributor::class.java.simpleName -> configureContributor(
        PSIPresentationBgRendererWrapper.wrapIfNecessary(SemanticFileSearchEverywhereContributor(initEvent)), contributor)
      ClassSearchEverywhereContributor::class.java.simpleName -> configureContributor(
        PSIPresentationBgRendererWrapper.wrapIfNecessary(SemanticClassSearchEverywhereContributor(initEvent)), contributor)
      SymbolSearchEverywhereContributor::class.java.simpleName -> configureContributor(
        PSIPresentationBgRendererWrapper.wrapIfNecessary(SemanticSymbolSearchEverywhereContributor(initEvent)), contributor)
      else -> contributor
    }
  }

  private fun configureContributor(newContributor: SearchEverywhereContributor<*>,
                                   parentContributor: SearchEverywhereContributor<*>): SearchEverywhereContributor<*> {
    // Make sure replacing contributor is disposed when [SearchEverywhereUI] is disposed
    // We achieve that by registering initial contributor, which is a child [Disposable] to [SearchEverywhereUI],
    // as a parent [Disposable] to a new contributor
    Disposer.register(parentContributor, newContributor)
    return newContributor
  }
}