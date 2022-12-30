package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatformTestCase

internal abstract class LightFeaturesProviderTestCase<T : SearchEverywhereElementFeaturesProvider>(providerClass: Class<T>)
  : FeaturesProviderTestCase, LightPlatformTestCase() {
  override val provider: SearchEverywhereElementFeaturesProvider by lazy {
    SearchEverywhereElementFeaturesProvider.EP_NAME.findExtensionOrFail(providerClass)
  }

  override val testProject: Project
    get() = project
}