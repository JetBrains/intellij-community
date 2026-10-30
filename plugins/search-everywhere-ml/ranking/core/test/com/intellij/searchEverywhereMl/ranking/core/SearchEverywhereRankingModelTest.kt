package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.openapi.progress.ProgressManager
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.testFramework.SeProvidersTestFixture
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.ranking.core.features.FeaturesProviderCache
import com.intellij.searchEverywhereMl.ranking.core.features.HeavyFeaturesProviderTestCase
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereFileFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.core.model.SearchEverywhereModelProvider
import com.intellij.testFramework.VfsTestUtil

internal abstract class SearchEverywhereRankingModelTest
  : HeavyFeaturesProviderTestCase<SearchEverywhereFileFeaturesProvider>(SearchEverywhereFileFeaturesProvider::class.java) {
  abstract val tab: SearchEverywhereTab.TabWithMlRanking
  private val featuresProviders by lazy { SearchEverywhereElementFeaturesProvider.getFeatureProviders() }
  protected open val model by lazy { SearchEverywhereModelProvider().getModel(tab) }
  private val seProviders by lazy { SeProvidersTestFixture.createBlocking(project, testRootDisposable) }

  /** Searches for [searchQuery] in the files provider of Search Everywhere. */
  protected fun filterElements(searchQuery: String): List<SeItem> {
    return seProviders.searchBlocking(SeProviderIdUtils.FILES_ID, searchQuery)
  }

  protected fun performSearchFor(searchQuery: String, featuresProviderCache: FeaturesProviderCache? = null): RankingAssertion {
    VfsTestUtil.syncRefresh()
    val foundItems = filterElements(searchQuery)

    lateinit var rankedElements: List<SeItem>
    ProgressManager.getInstance().executeNonCancelableSection {
      rankedElements = foundItems
        .associateWith { getMlWeight(it, searchQuery, featuresProviderCache) }
        .entries
        .sortedByDescending { it.value }
        .map { it.key }
    }

    assert(rankedElements.size > 1) { "Found ${rankedElements.size} which is unsuitable for ranking assertions" }

    return RankingAssertion(rankedElements)
  }

  protected fun getMlWeight(item: SeItem,
                            searchQuery: String,
                            featuresProviderCache: FeaturesProviderCache?): Double {
    return model.predict(getElementFeatures(item, searchQuery, featuresProviderCache))
  }

  private fun getElementFeatures(foundItem: SeItem,
                                 searchQuery: String,
                                 featuresProviderCache: FeaturesProviderCache?): Map<String, Any?> {
    return featuresProviders.map {
      val features = it.getElementFeatures(foundItem.rawObject, System.currentTimeMillis(), searchQuery, foundItem.weight(),
                                           featuresProviderCache, SearchEverywhereSpellCheckResult.NoCorrection)
      val featuresAsMap = hashMapOf<String, Any?>()
      for (feature in features) {
        featuresAsMap[feature.field.name] = feature.data
      }
      featuresAsMap
    }.fold(emptyMap()) { acc, value -> acc + value }
  }

  @Suppress("unused")
  protected class RankingAssertion(private val results: List<SeItem>) {
    fun thenAssertElement(element: SeItem) = ElementAssertion(element)

    @Suppress("unused")
    inner class ElementAssertion(private val element: SeItem) {
      fun isWithinTop(n: Int) {
        val errorMessage = "The index of the element is actually ${results.indexOf(element)}, it's not within the top $n."
        val top = n.coerceAtMost(results.size)
        assertTrue(errorMessage, results.subList(0, top).contains(element))
      }

      fun isAtIndex(index: Int) {
        val errorMessage = "The index of the element is actually ${results.indexOf(element)}, not ${index} as expected."
        assertEquals(errorMessage, element, results[index])
      }
    }

    /** Finds the found item whose element matches [predicate]. See [SeProvidersTestFixture.elementOf]. */
    fun findElementAndAssert(predicate: (Any) -> Boolean) = ElementAssertion(results.find { predicate(SeProvidersTestFixture.elementOf(it)) }!!)
  }
}
