package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereStateFeaturesProvider.Companion.QUERY_CONTAINS_PATH_DATA_KEY
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SearchEverywhereStateFeaturesProviderTest : BasePlatformTestCase() {
  private val featuresProvider = SearchEverywhereStateFeaturesProvider()

  fun `test contains path feature exists in all tab`() {
    val features = featuresProvider.getSearchStateFeatures(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID, "foo")
    assertTrue(QUERY_CONTAINS_PATH_DATA_KEY in features)
  }

  fun `test contains path feature exists in files tab`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "foo")
    assertTrue(QUERY_CONTAINS_PATH_DATA_KEY in features)
  }

  fun `test contains path feature is false when no path specified`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "foo")
    val containsPath = features[QUERY_CONTAINS_PATH_DATA_KEY] as Boolean

    assertFalse(containsPath)
  }

  fun `test contains path feature is false when nothing before slash`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "/foo")
    val containsPath = features[QUERY_CONTAINS_PATH_DATA_KEY] as Boolean

    assertFalse(containsPath)
  }

  fun `test contains path feature is true with one slash`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "bar/foo")
    val containsPath = features[QUERY_CONTAINS_PATH_DATA_KEY] as Boolean

    assertTrue(containsPath)
  }

  fun `test contains path feature is true with multiple slashes`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "/x/bar/foo")
    val containsPath = features[QUERY_CONTAINS_PATH_DATA_KEY] as Boolean

    assertTrue(containsPath)
  }
}