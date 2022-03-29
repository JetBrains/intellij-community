package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereElementFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereModelProvider
import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereRankingModel
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.ChooseByNameViewModel
import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager

internal abstract class SearchEverywhereRankingModelTest
  : HeavyFeaturesProviderTestCase<SearchEverywhereFileFeaturesProvider>(SearchEverywhereFileFeaturesProvider::class.java) {
  abstract val tab: SearchEverywhereTabWithMl
  private val featuresProviders by lazy { SearchEverywhereElementFeaturesProvider.getFeatureProviders() }
  protected val model by lazy { SearchEverywhereRankingModel(SearchEverywhereModelProvider().getModel(tab.tabId)) }
  protected val mockProgressIndicator by lazy { MockProgressIndicator() }

  protected abstract fun filterElements(searchQuery: String): List<FoundItemDescriptor<*>>

  protected fun performSearchFor(searchQuery: String): RankingAssertion {
    VirtualFileManager.getInstance().syncRefresh()
    val rankedElements: List<FoundItemDescriptor<*>> = filterElements(searchQuery)
      .map { it.withMlWeight(getMlWeight(it, searchQuery)) }
      .sortedByDescending { it.mlWeight }

    return RankingAssertion(rankedElements)
  }

  private fun getMlWeight(item: FoundItemDescriptor<*>, searchQuery: String) = model.predict(getElementFeatures(item, searchQuery))

  private fun getElementFeatures(foundItem: FoundItemDescriptor<*>, searchQuery: String): Map<String, Any> {
    return featuresProviders.map { it.getElementFeatures(foundItem.item, System.currentTimeMillis(), searchQuery, foundItem.weight, null) }
      .fold(emptyMap()) { acc, value -> acc + value }
  }

  private fun FoundItemDescriptor<*>.withMlWeight(mlWeight: Double) = FoundItemDescriptor(this.item, this.weight, mlWeight)

  protected class RankingAssertion(private val results: List<FoundItemDescriptor<*>>) {
    fun thenAssertElement(element: FoundItemDescriptor<*>) = ElementAssertion(element)
    fun findElementAndAssert(predicate: (FoundItemDescriptor<*>) -> Boolean) = ElementAssertion(results.find(predicate)!!)

    inner class ElementAssertion(private val element: FoundItemDescriptor<*>) {
      fun isWithinTop(n: Int) {
        val top = n.coerceAtMost(results.size)
        assertTrue(results.subList(0, top).contains(element))
      }

      fun isAtIndex(index: Int) {
        assertEquals(element, results[index])
      }
    }
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