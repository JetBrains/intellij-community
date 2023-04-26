package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereCommonFeaturesProvider.Companion.PRIORITY_DATA_KEY


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
