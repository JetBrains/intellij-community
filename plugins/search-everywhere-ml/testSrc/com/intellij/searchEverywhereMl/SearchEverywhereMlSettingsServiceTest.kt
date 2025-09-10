package com.intellij.searchEverywhereMl

import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.junit5.TestApplication
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@TestApplication
@ResourceLock("SearchEverywhereMlSettingsService")
internal class SearchEverywhereMlSettingsServiceTest {
  @ParameterizedTest(name = "tab with no default ml ranking and no experiment has setting invisible - {0}")
  @MethodSource("allTabs")
  fun `tab with no default ml ranking and no experiment has setting invisible`(testCase: TabTestCase) {
    every { testCase.tab.currentExperimentType } returns SearchEverywhereMlExperiment.ExperimentType.NoExperiment
    every { testCase.tab.isMlRankingEnabledByDefault } returns false

    with(service<SearchEverywhereMlSettingsService>()) {
      setSettingsState(
        SearchEverywhereMlSettingsService.SettingsState().withTabState(testCase.tab, SearchEverywhereMlSettingsService.RankingState.DEFAULT_HEURISTICAL_RANKING)
      )
      forceSync()

      Assertions.assertFalse(getMlRankingEnabled(testCase.tab))
      Assertions.assertFalse(getMlRankingVisible(testCase.tab))
      Assertions.assertFalse(getMlRankingSettingEnabled(testCase.tab))
    }
  }

  @ParameterizedTest(name = "tab with no default ml ranking but has an active ML ranking experiment has setting visible - {0}")
  @MethodSource("allTabs")
  fun `tab with no default ml ranking but has an active ML ranking experiment has setting visible`(
    testCase: TabTestCase
  ) {
    every { testCase.tab.currentExperimentType } returns SearchEverywhereMlExperiment.ExperimentType.ExperimentalModel
    every { testCase.tab.isMlRankingEnabledByDefault } returns false

    with(service<SearchEverywhereMlSettingsService>()) {
      setSettingsState(
        SearchEverywhereMlSettingsService.SettingsState().withTabState(testCase.tab, SearchEverywhereMlSettingsService.RankingState.DEFAULT_HEURISTICAL_RANKING)
      )
      forceSync()

      Assertions.assertTrue(getMlRankingEnabled(testCase.tab))
      Assertions.assertTrue(getMlRankingVisible(testCase.tab))
      Assertions.assertTrue(getMlRankingSettingEnabled(testCase.tab))
    }
  }

  @ParameterizedTest(name = "tab with no default ml ranking and experiment that does not enable ML has setting invisible - {0}")
  @MethodSource("allTabs")
  fun `tab with no default ml ranking and experiment that does not enable ML has setting invisible`(
    testCase: TabTestCase
  ) {
    every { testCase.tab.currentExperimentType } returns SearchEverywhereMlExperiment.ExperimentType.NoMl
    every { testCase.tab.isMlRankingEnabledByDefault } returns false

    with(service<SearchEverywhereMlSettingsService>()) {
      setSettingsState(
        SearchEverywhereMlSettingsService.SettingsState().withTabState(testCase.tab, SearchEverywhereMlSettingsService.RankingState.DEFAULT_HEURISTICAL_RANKING)
      )

      Assertions.assertFalse(getMlRankingEnabled(testCase.tab))
      Assertions.assertFalse(getMlRankingVisible(testCase.tab))
      Assertions.assertFalse(getMlRankingSettingEnabled(testCase.tab))
    }
  }

  @ParameterizedTest(name = "tab with enabled default ranking and no experiment has setting enabled - {0}")
  @MethodSource("allTabs")
  fun `tab with enabled default ranking and no experiment has setting enabled`(
    testCase: TabTestCase
  ) {
    every { testCase.tab.currentExperimentType } returns SearchEverywhereMlExperiment.ExperimentType.NoExperiment
    every { testCase.tab.isMlRankingEnabledByDefault } returns true

    service<SearchEverywhereMlSettingsService>().setSettingsState(
      SearchEverywhereMlSettingsService.SettingsState().withTabState(testCase.tab, SearchEverywhereMlSettingsService.RankingState.RANKING_WITH_DEFAULT_MODEL)
    )

    val isSettingVisible = service<SearchEverywhereMlSettingsService>().getMlRankingVisible(testCase.tab)
    Assertions.assertTrue(isSettingVisible)
  }

  @ParameterizedTest(name = "tab with default ml ranking and experiment that disables ml has setting invisible - {0}")
  @MethodSource("allTabs")
  fun `tab with default ml ranking and experiment that disables ml has setting invisible`(
    testCase: TabTestCase
  ) {
    every { testCase.tab.currentExperimentType } returns SearchEverywhereMlExperiment.ExperimentType.NoMl
    every { testCase.tab.isMlRankingEnabledByDefault } returns true

    with(service<SearchEverywhereMlSettingsService>()) {
      setSettingsState(
        SearchEverywhereMlSettingsService.SettingsState().withTabState(testCase.tab, SearchEverywhereMlSettingsService.RankingState.RANKING_WITH_DEFAULT_MODEL)
      )
      forceSync()

      Assertions.assertFalse(getMlRankingEnabled(testCase.tab))
      Assertions.assertFalse(getMlRankingVisible(testCase.tab))
      Assertions.assertFalse(getMlRankingSettingEnabled(testCase.tab))
    }
  }

  @ParameterizedTest(name = "tab with disabled ranking by user will not have ml ranking re-enabled by experiment - {0}")
  @MethodSource("allTabs")
  fun `tab with disabled ranking by user will not have ml ranking re-enabled by experiment`(
    testCase: TabTestCase
  ) {
    every { testCase.tab.currentExperimentType } returns SearchEverywhereMlExperiment.ExperimentType.ExperimentalModel
    every { testCase.tab.isMlRankingEnabledByDefault } returns true

    with(service<SearchEverywhereMlSettingsService>()) {
      setMlRankingEnabled(testCase.tab, false)  // Essentially mocking the user's unticking the checkbox
      forceSync()  // This is vital - here we check that the sync respects user's preference

      Assertions.assertTrue(getMlRankingVisible(testCase.tab))
      Assertions.assertTrue(getMlRankingSettingEnabled(testCase.tab))
      Assertions.assertFalse(getMlRankingEnabled(testCase.tab))
    }
  }

  @ParameterizedTest(name = "re-enabling experiments through registry updates the tab state - {0}")
  @MethodSource("allTabs")
  fun `re-enabling experiments through registry updates the tab state`(
    testCase: TabTestCase
  ) {
    Registry.get(testCase.tab.disableExperimentRegistryKey).setValue(true)
    every { testCase.tab.currentExperimentType } returns SearchEverywhereMlExperiment.ExperimentType.NoExperiment
    every { testCase.tab.isMlRankingEnabledByDefault } returns true

    with(service<SearchEverywhereMlSettingsService>()) {
      setSettingsState(SearchEverywhereMlSettingsService.SettingsState().withTabState(testCase.tab, SearchEverywhereMlSettingsService.RankingState.RANKING_WITH_DEFAULT_MODEL))
      forceSync()

      every { testCase.tab.currentExperimentType } returns SearchEverywhereMlExperiment.ExperimentType.NoMl
      Registry.get(testCase.tab.disableExperimentRegistryKey).setValue(false) // triggers a listener

      Assertions.assertFalse(getMlRankingEnabled(testCase.tab))
      Assertions.assertFalse(getMlRankingSettingEnabled(testCase.tab))
      Assertions.assertFalse(getMlRankingVisible(testCase.tab))
    }
  }

  @ParameterizedTest(name = "disabling experiments does not affect settings when ranking is enabled by default - {0}")
  @MethodSource("allTabs")
  fun `disabling experiments does not affect settings when ranking is enabled by default`(
    testCase: TabTestCase
  ) {
    Registry.get(testCase.tab.disableExperimentRegistryKey).setValue(true)
    every { testCase.tab.currentExperimentType } returns SearchEverywhereMlExperiment.ExperimentType.NoExperiment
    every { testCase.tab.isMlRankingEnabledByDefault } returns true

    with(service<SearchEverywhereMlSettingsService>()) {
      setSettingsState(SearchEverywhereMlSettingsService.SettingsState().withTabState(testCase.tab, SearchEverywhereMlSettingsService.RankingState.RANKING_WITH_DEFAULT_MODEL))
      forceSync()

      Assertions.assertTrue(getMlRankingEnabled(testCase.tab))
      Assertions.assertTrue(getMlRankingSettingEnabled(testCase.tab))
      Assertions.assertTrue(getMlRankingVisible(testCase.tab))
    }
  }

  @ParameterizedTest(name = "user preference takes precedence over registry - {0}")
  @MethodSource("allTabs")
  fun `user preference takes precedence over registry`(
    testCase: TabTestCase
  ) {
    // We are testing the following case:
    // 1. ML is enabled by default
    // 2. User disables it
    // 3. User disables experiments
    // 4. User re-enables experiments
    // Here, we need to make sure that the ML ranking stays off, as per the user preference.

    with(service<SearchEverywhereMlSettingsService>()) {
      // 1. ML enabled by default
      every { testCase.tab.currentExperimentType } returns SearchEverywhereMlExperiment.ExperimentType.NoExperiment
      every { testCase.tab.isMlRankingEnabledByDefault } returns true
      setSettingsState(SearchEverywhereMlSettingsService.SettingsState().withTabState(testCase.tab, SearchEverywhereMlSettingsService.RankingState.RANKING_WITH_DEFAULT_MODEL))
      forceSync()

      Assertions.assertTrue(getMlRankingEnabled(testCase.tab))
      Assertions.assertTrue(getMlRankingSettingEnabled(testCase.tab))
      Assertions.assertTrue(getMlRankingVisible(testCase.tab))

      // 2. Disable ML as a user
      setMlRankingEnabled(testCase.tab, false)

      Assertions.assertFalse(getMlRankingEnabled(testCase.tab))
      Assertions.assertTrue(getMlRankingSettingEnabled(testCase.tab))
      Assertions.assertTrue(getMlRankingVisible(testCase.tab))

      // 3. Disable experiments
      every { testCase.tab.currentExperimentType } returns SearchEverywhereMlExperiment.ExperimentType.NoExperiment
      Registry.get(testCase.tab.disableExperimentRegistryKey).setValue(true)

      Assertions.assertFalse(getMlRankingEnabled(testCase.tab))
      Assertions.assertTrue(getMlRankingSettingEnabled(testCase.tab))
      Assertions.assertTrue(getMlRankingVisible(testCase.tab))

      // 4. Re-enable the experiments
      every { testCase.tab.currentExperimentType } returns SearchEverywhereMlExperiment.ExperimentType.ExperimentalModel
      Registry.get(testCase.tab.disableExperimentRegistryKey).setValue(false)

      Assertions.assertFalse(getMlRankingEnabled(testCase.tab))
      Assertions.assertTrue(getMlRankingSettingEnabled(testCase.tab))
      Assertions.assertTrue(getMlRankingVisible(testCase.tab))
    }
  }

  // Helper methods to dynamically access tab-specific properties
  private fun SearchEverywhereMlSettingsService.getMlRankingEnabled(tab: SearchEverywhereTab.TabWithMlRanking): Boolean {
    return when (tab) {
      SearchEverywhereTab.All -> enableAllTabMlRanking
      SearchEverywhereTab.Actions -> enableActionsTabMlRanking
      SearchEverywhereTab.Classes -> enableClassesTabMlRanking
      SearchEverywhereTab.Files -> enableFilesTabMlRanking
    }
  }

  private fun SearchEverywhereMlSettingsService.setMlRankingEnabled(tab: SearchEverywhereTab.TabWithMlRanking, enabled: Boolean) {
    when (tab) {
      SearchEverywhereTab.All -> enableAllTabMlRanking = enabled
      SearchEverywhereTab.Actions -> enableActionsTabMlRanking = enabled
      SearchEverywhereTab.Classes -> enableClassesTabMlRanking = enabled
      SearchEverywhereTab.Files -> enableFilesTabMlRanking = enabled
    }
  }

  private fun SearchEverywhereMlSettingsService.getMlRankingVisible(tab: SearchEverywhereTab.TabWithMlRanking): Boolean {
    return when (tab) {
      SearchEverywhereTab.All -> isEnableAllTabMlRankingVisible()
      SearchEverywhereTab.Actions -> isEnableActionsTabMlRankingVisible()
      SearchEverywhereTab.Classes -> isEnableClassesTabMlRankingVisible()
      SearchEverywhereTab.Files -> isEnableFilesTabMlRankingVisible()
    }
  }

  private fun SearchEverywhereMlSettingsService.getMlRankingSettingEnabled(tab: SearchEverywhereTab.TabWithMlRanking): Boolean {
    return when (tab) {
      SearchEverywhereTab.All -> isEnableAllTabMlRankingEnabled()
      SearchEverywhereTab.Actions -> isEnableActionsTabMlRankingEnabled()
      SearchEverywhereTab.Classes -> isEnableClassesTabMlRankingEnabled()
      SearchEverywhereTab.Files -> isEnableFilesTabMlRankingEnabled()
    }
  }

  // Data classes for test cases
  data class TabTestCase(val tab: SearchEverywhereTab.TabWithMlRanking) {
    override fun toString(): String = tab.tabId
  }

  @BeforeEach
  fun setUp() {
    mockkObject(SearchEverywhereMlRegistry)
    every { SearchEverywhereMlRegistry.isExperimentDisabled(any()) } returns false

    SearchEverywhereTab.allTabs
      .filterIsInstance<SearchEverywhereTab.TabWithMlRanking>()
      .forEach {
        mockkObject(it)
        every { it.currentExperimentType } returns SearchEverywhereMlExperiment.ExperimentType.NoExperiment
        every { it.isMlRankingEnabledByDefault } returns false
      }
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  companion object {
    @JvmStatic
    fun allTabs(): List<Arguments> {
      return SearchEverywhereTab.allTabs
        .filterIsInstance<SearchEverywhereTab.TabWithMlRanking>()
        .map { tab ->
          Arguments.of(TabTestCase(tab))
        }
    }
  }
}