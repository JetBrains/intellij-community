package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereElementFeaturesProvider
import com.intellij.openapi.project.Project
import com.intellij.testFramework.HeavyPlatformTestCase

abstract class HeavyFeaturesProviderTestCase<T : SearchEverywhereElementFeaturesProvider>(providerClass: Class<T>)
  : HeavyPlatformTestCase(), FeaturesProviderTestCase {
  override val provider: SearchEverywhereElementFeaturesProvider by lazy {
    SearchEverywhereElementFeaturesProvider.EP_NAME.findExtensionOrFail(providerClass)
  }

  override val testProject: Project
    get() = project
}