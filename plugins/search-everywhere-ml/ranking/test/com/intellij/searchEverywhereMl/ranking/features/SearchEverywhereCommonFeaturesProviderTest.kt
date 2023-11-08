package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereCommonFeaturesProvider.Fields.PRIORITY_DATA_KEY


internal class SearchEverywhereCommonFeaturesProviderTest
  : LightFeaturesProviderTestCase<SearchEverywhereCommonFeaturesProvider>(SearchEverywhereCommonFeaturesProvider::class.java) {

  fun testPriority() {
    val priority = 10101

    checkThatFeature(PRIORITY_DATA_KEY)
      .ofElement(Any())
      .withPriority(priority)
      .isEqualTo(priority)
  }
}
