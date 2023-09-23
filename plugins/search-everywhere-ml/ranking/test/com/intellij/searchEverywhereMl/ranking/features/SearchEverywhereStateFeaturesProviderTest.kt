package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereStateFeaturesProvider.Companion.QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereStateFeaturesProvider.Companion.QUERY_CONTAINS_PATH_DATA_KEY
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SearchEverywhereStateFeaturesProviderTest : BasePlatformTestCase() {
  private val featuresProvider = SearchEverywhereStateFeaturesProvider()

  fun `test contains path feature exists in all tab`() {
    val features = featuresProvider.getSearchStateFeatures(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID, "foo", project, null, false)
    assertNotNull(features.findFeature(QUERY_CONTAINS_PATH_DATA_KEY))
  }

  fun `test contains path feature exists in files tab`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "foo", project, null, false)
    assertNotNull(features.findFeature(QUERY_CONTAINS_PATH_DATA_KEY))
  }

  fun `test contains path feature is false when no path specified`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "foo", project, null, false)
    val containsPath = features.getFeatureValue(QUERY_CONTAINS_PATH_DATA_KEY)

    assertFalse(containsPath)
  }

  fun `test contains path feature is false when nothing before slash`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "/foo", project, null, false)
    val containsPath = features.getFeatureValue(QUERY_CONTAINS_PATH_DATA_KEY)

    assertFalse(containsPath)
  }

  fun `test contains path feature is true with one slash`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "bar/foo", project, null, false)
    val containsPath = features.getFeatureValue(QUERY_CONTAINS_PATH_DATA_KEY)

    assertTrue(containsPath)
  }

  fun `test contains path feature is true with multiple slashes`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "/x/bar/foo", project, null, false)
    val containsPath = features.getFeatureValue(QUERY_CONTAINS_PATH_DATA_KEY)

    assertTrue(containsPath)
  }

  fun `test is abbreviation is true for uppercase queries`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "FPCDP", project, null, false)
    assertTrue(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is abbreviation is true for uppercase queries with spaces in between letters`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "F P CDP", project, null, false)
    assertTrue(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is abbreviation is false for mixed case queries`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "FPrCaDaP", project, null, false)
    assertFalse(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is abbreviation is false for mixed case queries with spaces in between word chunks`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "Fe Pr CaDaP", project, null, false)
    assertFalse(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is abbreviation is false for lowercase queries`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "fpcdp", project, null, false)
    assertFalse(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is uppercase is true for uppercase queries`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "FPCDP", project, null, false)
    assertTrue(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is uppercase is true for uppercase queries with spaces in between letters`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "F P CDP", project, null, false)
    assertTrue(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is uppercase is false for mixed case queries`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "Fe Pr CaDaP", project, null, false)
    assertFalse(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is uppercase is false for lowercase queries`() {
    val features = featuresProvider.getSearchStateFeatures(FileSearchEverywhereContributor::class.java.simpleName, "fpcdp", project, null, false)
    assertFalse(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }
}