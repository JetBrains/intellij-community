package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SearchEverywhereStateFeaturesProviderTest : BasePlatformTestCase() {
  fun `test contains path feature exists in all tab`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.All, "foo", null, false)
    assertNotNull(features.findFeature(SearchEverywhereStateFeaturesProvider. QUERY_CONTAINS_PATH_DATA_KEY))
  }

  fun `test contains path feature exists in files tab`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.Files, "foo", null, false)
    assertNotNull(features.findFeature(SearchEverywhereStateFeaturesProvider. QUERY_CONTAINS_PATH_DATA_KEY))
  }

  fun `test contains path feature is false when no path specified`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.Files, "foo", null, false)
    val containsPath = features.getFeatureValue(SearchEverywhereStateFeaturesProvider.QUERY_CONTAINS_PATH_DATA_KEY)

    assertFalse(containsPath)
  }

  fun `test contains path feature is false when nothing before slash`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.Files, "/foo", null, false)
    val containsPath = features.getFeatureValue(SearchEverywhereStateFeaturesProvider.QUERY_CONTAINS_PATH_DATA_KEY)

    assertFalse(containsPath)
  }

  fun `test contains path feature is true with one slash`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.Files, "bar/foo", null, false)
    val containsPath = features.getFeatureValue(SearchEverywhereStateFeaturesProvider.QUERY_CONTAINS_PATH_DATA_KEY)

    assertTrue(containsPath)
  }

  fun `test contains path feature is true with multiple slashes`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.Files, "/x/bar/foo", null, false)
    val containsPath = features.getFeatureValue(SearchEverywhereStateFeaturesProvider.QUERY_CONTAINS_PATH_DATA_KEY)

    assertTrue(containsPath)
  }

  fun `test is abbreviation is true for uppercase queries`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.Files, "FPCDP", null, false)
    assertTrue(features.getFeatureValue(SearchEverywhereStateFeaturesProvider.QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is abbreviation is true for uppercase queries with spaces in between letters`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.Files, "F P CDP", null, false)
    assertTrue(features.getFeatureValue(SearchEverywhereStateFeaturesProvider.QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is abbreviation is false for mixed case queries`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.Files, "FPrCaDaP", null, false)
    assertFalse(features.getFeatureValue(SearchEverywhereStateFeaturesProvider.QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is abbreviation is false for mixed case queries with spaces in between word chunks`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.Files, "Fe Pr CaDaP", null, false)
    assertFalse(features.getFeatureValue(SearchEverywhereStateFeaturesProvider.QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is abbreviation is false for lowercase queries`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.Files, "fpcdp", null, false)
    assertFalse(features.getFeatureValue(SearchEverywhereStateFeaturesProvider.QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is uppercase is true for uppercase queries`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.Files, "FPCDP", null, false)
    assertTrue(features.getFeatureValue(SearchEverywhereStateFeaturesProvider.QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is uppercase is true for uppercase queries with spaces in between letters`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.Files, "F P CDP", null, false)
    assertTrue(features.getFeatureValue(SearchEverywhereStateFeaturesProvider.QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is uppercase is false for mixed case queries`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.Files, "Fe Pr CaDaP", null, false)
    assertFalse(features.getFeatureValue(SearchEverywhereStateFeaturesProvider.QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is uppercase is false for lowercase queries`() {
    val features = SearchEverywhereStateFeaturesProvider.getFeatures(project, SearchEverywhereTab.Files, "fpcdp", null, false)
    assertFalse(features.getFeatureValue(SearchEverywhereStateFeaturesProvider.QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }
}