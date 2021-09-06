package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereElementFeaturesProvider
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

internal abstract class LightFeaturesProviderTestCase<T : SearchEverywhereElementFeaturesProvider>(providerClass: Class<T>)
  : FeaturesProviderTestCase, LightJavaCodeInsightFixtureTestCase() {
  override val provider: SearchEverywhereElementFeaturesProvider by lazy {
    SearchEverywhereElementFeaturesProvider.EP_NAME.findExtensionOrFail(providerClass)
  }

  override val testProject: Project
    get() = project

  override fun getTestDataPath(): String {
    return PluginPathManager.getPluginHomePath("search-everywhere-ml").plus("/testData")
  }
}