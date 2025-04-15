package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.ChooseByNameViewModel
import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
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
  protected val mockProgressIndicator by lazy { MockProgressIndicator() }

  protected abstract fun filterElements(searchQuery: String): List<FoundItemDescriptor<*>>

  protected fun performSearchFor(searchQuery: String, featuresProviderCache: FeaturesProviderCache? = null): RankingAssertion {
    VfsTestUtil.syncRefresh()

    lateinit var rankedElements: List<FoundItemDescriptor<*>>
    ProgressManager.getInstance().executeNonCancelableSection {
      rankedElements = filterElements(searchQuery)
        .associateWith { getMlWeight(it, searchQuery, featuresProviderCache) }
        .entries
        .sortedByDescending { it.value }
        .map { it.key }
    }

    assert(rankedElements.size > 1) { "Found ${rankedElements.size} which is unsuitable for ranking assertions" }

    return RankingAssertion(rankedElements)
  }

  protected fun getMlWeight(item: FoundItemDescriptor<*>,
                            searchQuery: String,
                            featuresProviderCache: FeaturesProviderCache?): Double {
    return model.predict(getElementFeatures(item, searchQuery, featuresProviderCache))
  }

  private fun getElementFeatures(foundItem: FoundItemDescriptor<*>,
                                 searchQuery: String,
                                 featuresProviderCache: FeaturesProviderCache?): Map<String, Any?> {
    return featuresProviders.map {
      val features = it.getElementFeatures(foundItem.item, System.currentTimeMillis(), searchQuery, foundItem.weight,
                                           featuresProviderCache)
      val featuresAsMap = hashMapOf<String, Any?>()
      for (feature in features) {
        featuresAsMap[feature.field.name] = feature.data
      }
      featuresAsMap
    }.fold(emptyMap()) { acc, value -> acc + value }
  }

  @Suppress("unused")
  protected class RankingAssertion(private val results: List<FoundItemDescriptor<*>>) {
    fun thenAssertElement(element: FoundItemDescriptor<*>) = ElementAssertion(element)

    @Suppress("unused")
    inner class ElementAssertion(private val element: FoundItemDescriptor<*>) {
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

    fun findElementAndAssert(predicate: (FoundItemDescriptor<*>) -> Boolean) = ElementAssertion(results.find(predicate)!!)
  }

  protected inner class StubChooseByNameViewModel(private val model: ChooseByNameModel) : ChooseByNameViewModel {
    override fun getProject(): Project = this@SearchEverywhereRankingModelTest.project

    override fun getModel(): ChooseByNameModel = model

    override fun isSearchInAnyPlace() = model.useMiddleMatching()

    override fun transformPattern(pattern: String) = ChooseByNamePopup.getTransformedPattern(pattern, model)

    override fun canShowListForEmptyPattern() = false

    override fun getMaximumListSizeLimit() = 0
  }
}