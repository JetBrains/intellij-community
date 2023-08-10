package com.intellij.searchEverywhereMl.semantics.contributors

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMlContributorReplacementService

class SearchEverywhereMlContributorReplacementServiceImpl : SearchEverywhereMlContributorReplacementService {
  override fun replaceInSeparateTab(contributor: SearchEverywhereContributor<*>): SearchEverywhereContributor<*> {
    return when (contributor.searchProviderId) {
      ActionSearchEverywhereContributor::class.java.simpleName ->
        SemanticActionSearchEverywhereContributor(contributor as ActionSearchEverywhereContributor)
      else -> contributor
    }
  }
}