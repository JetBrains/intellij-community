package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.openapi.util.Disposer
import com.intellij.searchEverywhereMl.SemanticSearchEverywhereContributor
import com.intellij.platform.ml.embeddings.search.settings.SemanticSearchSettings
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class SearchEverywhereMlContributorReplacementImpl : SearchEverywhereMlContributorReplacement {
  override fun replaceInSeparateTab(contributor: SearchEverywhereContributor<*>): SearchEverywhereContributor<*> {
    if (contributor is SemanticSearchEverywhereContributor) return contributor
    val settings = SemanticSearchSettings.getInstance()
    val initEvent = SearchEverywhereMlContributorReplacement.initEvent.get() ?: return contributor
    val searchProviderId = contributor.searchProviderId
    return if (isActionsContributor(searchProviderId) && settings.enabledInActionsTab) {
      configureContributor(SemanticActionSearchEverywhereContributor(contributor as ActionSearchEverywhereContributor), contributor)
    }
    else if (isFilesContributor(searchProviderId) && settings.enabledInFilesTab) {
      configureContributor(PSIPresentationBgRendererWrapper.wrapIfNecessary(
        SemanticFileSearchEverywhereContributor(initEvent)
      ), contributor)
    }
    else if (isSymbolsContributor(searchProviderId) && settings.enabledInSymbolsTab) {
      configureContributor(PSIPresentationBgRendererWrapper.wrapIfNecessary(
        SemanticSymbolSearchEverywhereContributor(initEvent)
      ), contributor)
    }
    else if (isClassesContributor(searchProviderId) && settings.enabledInClassesTab) {
      configureContributor(PSIPresentationBgRendererWrapper.wrapIfNecessary(
        SemanticClassSearchEverywhereContributor(initEvent)
      ), contributor)
    }
    else {
      contributor
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

  private fun isActionsContributor(searchProviderId: String): Boolean {
    return searchProviderId == ActionSearchEverywhereContributor::class.java.simpleName
  }

  private fun isFilesContributor(searchProviderId: String): Boolean {
    return searchProviderId == FileSearchEverywhereContributor::class.java.simpleName
  }

  private fun isClassesContributor(searchProviderId: String): Boolean {
    return searchProviderId == ClassSearchEverywhereContributor::class.java.simpleName
  }

  private fun isSymbolsContributor(searchProviderId: String): Boolean {
    return searchProviderId == SymbolSearchEverywhereContributor::class.java.simpleName
  }
}