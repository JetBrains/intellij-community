package com.intellij.searchEverywhereMl.ranking.core.adapters

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult

internal class TestSearchResultRawAdapter(
  override val provider: SearchResultProviderAdapter,
  override val originalWeight: Int,
  override val providerWeight: Int? = null,
  override val isSemantic: Boolean = false,
  override val correction: SearchEverywhereSpellCheckResult = SearchEverywhereSpellCheckResult.NoCorrection,
  override val stateLocalId: StateLocalId,
  private val rawItem: Any?,
) : SearchResultAdapter.Raw {
  override fun fetchRawItemIfExists(): Any? = rawItem
}
