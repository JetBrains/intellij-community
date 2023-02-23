package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereStateFeaturesProvider.Companion.IS_DUMB_MODE
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereStateFeaturesProvider.Companion.QUERY_CONTAINS_PATH_DATA_KEY
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SearchEverywhereStateFeaturesProviderTest : BasePlatformTestCase() {
  private val featuresProvider = SearchEverywhereStateFeaturesProvider()

  fun `test contains path feature exists in all tab`() {
    val features = featuresProvider.getSearchStateFeatures(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID, "foo", false, null, false)
    assertNotNull(findFeature(QUERY_CONTAINS_PATH_DATA_KEY, features))
  }

  fun `test contains path feature exists in files tab`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "foo", false, null, false)
    assertNotNull(findFeature(QUERY_CONTAINS_PATH_DATA_KEY, features))
  }

  fun `test contains path feature is false when no path specified`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "foo", false, null, false)
    val containsPath = findBooleanFeature(QUERY_CONTAINS_PATH_DATA_KEY, features)

    assertFalse(containsPath)
  }

  fun `test contains path feature is false when nothing before slash`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "/foo", false, null, false)
    val containsPath = findBooleanFeature(QUERY_CONTAINS_PATH_DATA_KEY, features)

    assertFalse(containsPath)
  }

  fun `test contains path feature is true with one slash`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "bar/foo", false, null, false)
    val containsPath = findBooleanFeature(QUERY_CONTAINS_PATH_DATA_KEY, features)

    assertTrue(containsPath)
  }

  fun `test contains path feature is true with multiple slashes`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "/x/bar/foo", false, null, false)
    val containsPath = findBooleanFeature(QUERY_CONTAINS_PATH_DATA_KEY, features)

    assertTrue(containsPath)
  }

  fun `test contains isDumbMode feature is true when isDumb is true`() {
    val featuresDumbFalse = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "/x/bar/foo", true, null, false)
    assertTrue(findBooleanFeature(IS_DUMB_MODE, featuresDumbFalse))
  }

  fun `test contains isDumbMode feature is null when isDumb null`() {
    val featuresDumbNull = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "/x/bar/foo", null, null, false)
    assertNull(findFeature(IS_DUMB_MODE, featuresDumbNull))
  }

  private fun findFeature(field: EventField<*>, features: List<EventPair<*>>): EventPair<*>? =
    features.find { field.name == it.field.name }

  private fun findBooleanFeature(field: EventField<*>, features: List<EventPair<*>>): Boolean=
    findFeature(field, features)!!.data as Boolean
}