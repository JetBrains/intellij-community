package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereStateFeaturesProvider.Companion.QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereStateFeaturesProvider.Companion.QUERY_CONTAINS_PATH_DATA_KEY
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SearchEverywhereStateFeaturesProviderTest : BasePlatformTestCase() {
  private val featuresProvider = SearchEverywhereStateFeaturesProvider()

  fun `test contains path feature exists in all tab`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.All, "foo", null, false)
    assertNotNull(features.findFeature(QUERY_CONTAINS_PATH_DATA_KEY))
  }

  fun `test contains path feature exists in files tab`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.Files, "foo", null, false)
    assertNotNull(features.findFeature(QUERY_CONTAINS_PATH_DATA_KEY))
  }

  fun `test contains path feature is false when no path specified`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.Files, "foo", null, false)
    val containsPath = features.getFeatureValue(QUERY_CONTAINS_PATH_DATA_KEY)

    assertFalse(containsPath)
  }

  fun `test contains path feature is false when nothing before slash`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.Files, "/foo", null, false)
    val containsPath = features.getFeatureValue(QUERY_CONTAINS_PATH_DATA_KEY)

    assertFalse(containsPath)
  }

  fun `test contains path feature is true with one slash`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.Files, "bar/foo", null, false)
    val containsPath = features.getFeatureValue(QUERY_CONTAINS_PATH_DATA_KEY)

    assertTrue(containsPath)
  }

  fun `test contains path feature is true with multiple slashes`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.Files, "/x/bar/foo", null, false)
    val containsPath = features.getFeatureValue(QUERY_CONTAINS_PATH_DATA_KEY)

    assertTrue(containsPath)
  }

  fun `test is abbreviation is true for uppercase queries`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.Files, "FPCDP", null, false)
    assertTrue(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is abbreviation is true for uppercase queries with spaces in between letters`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.Files, "F P CDP", null, false)
    assertTrue(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is abbreviation is false for mixed case queries`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.Files, "FPrCaDaP", null, false)
    assertFalse(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is abbreviation is false for mixed case queries with spaces in between word chunks`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.Files, "Fe Pr CaDaP", null, false)
    assertFalse(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is abbreviation is false for lowercase queries`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.Files, "fpcdp", null, false)
    assertFalse(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is uppercase is true for uppercase queries`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.Files, "FPCDP", null, false)
    assertTrue(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is uppercase is true for uppercase queries with spaces in between letters`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.Files, "F P CDP", null, false)
    assertTrue(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is uppercase is false for mixed case queries`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.Files, "Fe Pr CaDaP", null, false)
    assertFalse(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }

  fun `test is uppercase is false for lowercase queries`() {
    val features = featuresProvider.getSearchStateFeatures(project, SearchEverywhereTab.Files, "fpcdp", null, false)
    assertFalse(features.getFeatureValue(QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY))
  }
}