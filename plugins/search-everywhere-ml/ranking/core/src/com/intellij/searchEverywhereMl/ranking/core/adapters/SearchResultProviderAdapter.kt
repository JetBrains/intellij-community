package com.intellij.searchEverywhereMl.ranking.core.adapters

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor

sealed interface SearchResultProviderAdapter {
  val id: String

  companion object {
    fun createAdapterFor(contributor: SearchEverywhereContributor<*>): SearchResultProviderAdapter {
      return LegacyContributorAdapter(contributor)
    }

    fun createAdapterFor(providerId: String): SearchResultProviderAdapter {
      return DefaultSearchResultProviderAdapter(providerId)
    }
  }
}

private data class DefaultSearchResultProviderAdapter(override val id: String): SearchResultProviderAdapter

internal class LegacyContributorAdapter(val contributor: SearchEverywhereContributor<*>) : SearchResultProviderAdapter {
  override val id: String
    get() = contributor.searchProviderId

  override fun equals(other: Any?): Boolean {
    if (other !is SearchResultProviderAdapter) {
      return false
    }

    return id == other.id
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }
}
