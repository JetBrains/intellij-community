package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.testFramework.LightPlatformTestCase

class SearchEverywhereFeatureProvidersTest: LightPlatformTestCase() {

  fun `test features from different providers don't intersect`() {
    val featureNameToProvider = hashMapOf<String, SearchEverywhereElementFeaturesProvider>()
    for (provider in SearchEverywhereElementFeaturesProvider.getFeatureProviders()) {
      for (feature in provider.getFeaturesDeclarations()) {
        val name = feature.name
        assertFalse(
          "Feature '${name}' is reported by both " +
          "${featureNameToProvider[name]?.javaClass?.simpleName} and ${provider.javaClass.simpleName} providers",
          featureNameToProvider.containsKey(name)
        )
        featureNameToProvider[name] = provider
      }
    }
  }
}