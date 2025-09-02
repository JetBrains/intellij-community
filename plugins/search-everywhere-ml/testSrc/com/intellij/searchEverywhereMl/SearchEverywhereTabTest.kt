package com.intellij.searchEverywhereMl

import com.intellij.openapi.options.advanced.AdvancedSettingBean
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment.ExperimentType.ActiveExperiment
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert

internal class SearchEverywhereTabTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    SearchEverywhereMlExperiment.isExperimentalMode = true
  }

  fun `test experiment disables ml ranking if it was enabled by default`() {
    val (tab, experimentGroupWithoutMlRanking) = SearchEverywhereTab.findTabWithActiveExperiment { !it.shouldSortByMl } ?: return

    // We will now replace the setting for this tab and set up the prerequisites for the test, which are:
    // 1. The tab has ML enabled by default (via Advanced Settings)
    // 2. The tab has the default setting
    val setting = AdvancedSettingBean().apply {
      id = tab.advancedSettingKey
      defaultValue = "true"
    }
    ExtensionTestUtil.maskExtensions(AdvancedSettingBean.EP_NAME, listOf(setting), testRootDisposable)
    AdvancedSettings.setBoolean(tab.advancedSettingKey, true)

    // Now, we will change the experiment to one that would normally enable ML ranking,
    // and we will check that the ML stays disabled as per the user's setting
    SearchEverywhereMlExperiment.experimentGroupOverride = experimentGroupWithoutMlRanking

    Assert.assertFalse(tab.isMlRankingEnabled)
  }

  fun `test experiment enables ml ranking if it was disabled by default`() {
    val (tab, experimentGroupWithoutMlRanking) = SearchEverywhereTab.findTabWithActiveExperiment { it.shouldSortByMl } ?: return

    // We will now replace the setting for this tab and set up the prerequisites for the test, which are:
    // 1. The tab has ML enabled by default (via Advanced Settings)
    // 2. The tab has the default setting
    val setting = AdvancedSettingBean().apply {
      id = tab.advancedSettingKey
      defaultValue = "false"
    }
    ExtensionTestUtil.maskExtensions(AdvancedSettingBean.EP_NAME, listOf(setting), testRootDisposable)
    AdvancedSettings.setBoolean(tab.advancedSettingKey, false)

    // Now, we will change the experiment to one that would normally enable ML ranking,
    // and we will check that the ML stays disabled as per the user's setting
    SearchEverywhereMlExperiment.experimentGroupOverride = experimentGroupWithoutMlRanking

    Assert.assertTrue(tab.isMlRankingEnabled)
  }

  fun `test experiment does not re-enable ml ranking after it was disabled`() {
    val (tab, experimentGroupWithMlRanking) = SearchEverywhereTab.findTabWithActiveExperiment { it.shouldSortByMl } ?: return

    // We will now replace the setting for this tab and set up the prerequisites for the test, which are:
    // 1. The tab has ML enabled by default (via Advanced Settings)
    // 2. The tab has ML disabled (default value changed) - indicates possible unsatisfaction with the ML ranking
    val setting = AdvancedSettingBean().apply {
      id = tab.advancedSettingKey
      defaultValue = "true"
    }
    ExtensionTestUtil.maskExtensions(AdvancedSettingBean.EP_NAME, listOf(setting), testRootDisposable)
    AdvancedSettings.setBoolean(tab.advancedSettingKey, false)

    // Now, we will change the experiment to one that would normally enable ML ranking,
    // and we will check that the ML stays disabled as per the user's setting
    SearchEverywhereMlExperiment.experimentGroupOverride = experimentGroupWithMlRanking

    Assert.assertFalse(tab.isMlRankingEnabled)
  }

  private fun SearchEverywhereTab.Companion.findTabWithActiveExperiment(experimentCondition: (ActiveExperiment) -> Boolean): Pair<SearchEverywhereTab.TabWithMlRanking, Int>? {
   return SearchEverywhereTab.allTabs
     .asSequence()
     .filterIsInstance<SearchEverywhereTab.TabWithMlRanking>()
     .associateWith { tab ->
       tab.experiments.firstNotNullOfOrNull { (groupNumber, experimentType) ->
         if (experimentType is ActiveExperiment && experimentCondition(experimentType)) {
           groupNumber  // The group number of this experiment
         } else {
           null
         }
       }
     }.firstNotNullOfOrNull { (tab, experimentGroupWithMlRanking) ->
       if (experimentGroupWithMlRanking == null) {
         null
       } else {
         tab to experimentGroupWithMlRanking
       }
     }
  }

  override fun tearDown() {
    super.tearDown()
    SearchEverywhereMlExperiment.experimentGroupOverride = null
  }
}