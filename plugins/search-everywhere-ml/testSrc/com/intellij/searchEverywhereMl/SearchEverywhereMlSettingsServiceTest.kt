package com.intellij.searchEverywhereMl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.options.advanced.AdvancedSettingBean
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment.ExperimentType.ActiveExperiment
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
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
  private val tabsWithMlRanking = SearchEverywhereTab.tabsWithLogging.filterIsInstance<SearchEverywhereTab.TabWithMlRanking>()
  private lateinit var testDisposable: Disposable
  private lateinit var originalTabSettingValues: Map<SearchEverywhereTab.TabWithMlRanking, Boolean>
  private lateinit var originalTabSettingDefaults: Map<SearchEverywhereTab.TabWithMlRanking, Boolean>
  private lateinit var originalRegistryValues: Map<String, Boolean>
  private var originalIsExperimentalMode: Boolean = false
  private var originalExperimentGroupOverride: Int? = null

  @ParameterizedTest(name = "tab with no default ml ranking and no experiment has setting invisible - {0}")
  @MethodSource("allTabs")
  fun `tab with no default ml ranking and no experiment has setting invisible`(testCase: TabTestCase) {
    prepareTabState(testCase, isMlRankingEnabledByDefault = false, experimentScenario = ExperimentScenario.NoExperiment)

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
    prepareTabState(testCase, isMlRankingEnabledByDefault = false, experimentScenario = ExperimentScenario.EnabledByExperiment)

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
    prepareTabState(testCase, isMlRankingEnabledByDefault = false, experimentScenario = ExperimentScenario.DisabledByExperiment)

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
    prepareTabState(testCase, isMlRankingEnabledByDefault = true, experimentScenario = ExperimentScenario.NoExperiment)

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
    prepareTabState(testCase, isMlRankingEnabledByDefault = true, experimentScenario = ExperimentScenario.DisabledByExperiment)

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
    prepareTabState(testCase, isMlRankingEnabledByDefault = true, experimentScenario = ExperimentScenario.EnabledByExperiment)

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
    prepareTabState(testCase, isMlRankingEnabledByDefault = true, experimentScenario = ExperimentScenario.NoExperiment)
    Registry.get(testCase.tab.disableExperimentRegistryKey).setValue(true)

    with(service<SearchEverywhereMlSettingsService>()) {
      setSettingsState(SearchEverywhereMlSettingsService.SettingsState().withTabState(testCase.tab, SearchEverywhereMlSettingsService.RankingState.RANKING_WITH_DEFAULT_MODEL))
      forceSync()

      SearchEverywhereMlExperiment.experimentGroupOverride = testCase.mlDisabledExperimentGroup
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
    prepareTabState(testCase, isMlRankingEnabledByDefault = true, experimentScenario = ExperimentScenario.NoExperiment)
    Registry.get(testCase.tab.disableExperimentRegistryKey).setValue(true)

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
    prepareTabState(testCase, isMlRankingEnabledByDefault = true, experimentScenario = ExperimentScenario.NoExperiment)
    // We are testing the following case:
    // 1. ML is enabled by default
    // 2. User disables it
    // 3. User disables experiments
    // 4. User re-enables experiments
    // Here, we need to make sure that the ML ranking stays off, as per the user preference.

    with(service<SearchEverywhereMlSettingsService>()) {
      // 1. ML enabled by default
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
      SearchEverywhereMlExperiment.experimentGroupOverride = testCase.noExperimentGroup
      Registry.get(testCase.tab.disableExperimentRegistryKey).setValue(true)

      Assertions.assertFalse(getMlRankingEnabled(testCase.tab))
      Assertions.assertTrue(getMlRankingSettingEnabled(testCase.tab))
      Assertions.assertTrue(getMlRankingVisible(testCase.tab))

      // 4. Re-enable the experiments
      SearchEverywhereMlExperiment.experimentGroupOverride = testCase.mlEnabledExperimentGroup
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
    val noExperimentGroup: Int = (0 until SearchEverywhereMlExperiment.NUMBER_OF_GROUPS).first { it !in tab.experiments.keys }
    val mlEnabledExperimentGroup: Int =
      tab.experiments.entries.firstNotNullOfOrNull { (group, experimentType) ->
        (experimentType as? ActiveExperiment)?.takeIf { it.shouldSortByMl }?.let { group }
      } ?: error("No experiment with ML enabled found for ${tab.tabId}")
    val mlDisabledExperimentGroup: Int =
      tab.experiments.entries.firstNotNullOfOrNull { (group, experimentType) ->
        (experimentType as? ActiveExperiment)?.takeIf { !it.shouldSortByMl }?.let { group }
      } ?: error("No experiment with ML disabled found for ${tab.tabId}")

    override fun toString(): String = tab.tabId
  }

  @BeforeEach
  fun setUp() {
    testDisposable = Disposer.newDisposable()
    originalTabSettingValues = tabsWithMlRanking.associateWith { AdvancedSettings.getBoolean(it.advancedSettingKey) }
    originalTabSettingDefaults = tabsWithMlRanking.associateWith { it.isMlRankingEnabledByDefault }
    originalRegistryValues = (SearchEverywhereMlRegistry.ALL_DISABLE_EXPERIMENT_KEYS + DISABLE_LOGGING_REGISTRY_KEY).associateWith {
      Registry.`is`(it, false)
    }
    originalIsExperimentalMode = SearchEverywhereMlExperiment.isExperimentalMode
    originalExperimentGroupOverride = SearchEverywhereMlExperiment.experimentGroupOverride
    resetAllRegistryOverrides()
  }

  @AfterEach
  fun tearDown() {
    tabsWithMlRanking.forEach {
      AdvancedSettings.setBoolean(it.advancedSettingKey, originalTabSettingValues.getValue(it))
    }
    originalRegistryValues.forEach { (key, value) ->
      Registry.get(key).setValue(value)
    }
    SearchEverywhereMlExperiment.isExperimentalMode = originalIsExperimentalMode
    SearchEverywhereMlExperiment.experimentGroupOverride = originalExperimentGroupOverride
    Disposer.dispose(testDisposable)
  }

  private enum class ExperimentScenario {
    NoExperiment,
    EnabledByExperiment,
    DisabledByExperiment
  }

  private fun prepareTabState(
    testCase: TabTestCase,
    isMlRankingEnabledByDefault: Boolean,
    experimentScenario: ExperimentScenario
  ) {
    maskAdvancedSettingDefaults(testCase.tab, isMlRankingEnabledByDefault)
    AdvancedSettings.setBoolean(testCase.tab.advancedSettingKey, isMlRankingEnabledByDefault)
    SearchEverywhereMlExperiment.isExperimentalMode = true
    SearchEverywhereMlExperiment.experimentGroupOverride = when (experimentScenario) {
      ExperimentScenario.NoExperiment -> testCase.noExperimentGroup
      ExperimentScenario.EnabledByExperiment -> testCase.mlEnabledExperimentGroup
      ExperimentScenario.DisabledByExperiment -> testCase.mlDisabledExperimentGroup
    }
    resetAllRegistryOverrides()
  }

  private fun maskAdvancedSettingDefaults(
    tab: SearchEverywhereTab.TabWithMlRanking,
    isMlRankingEnabledByDefault: Boolean
  ) {
    val settings = tabsWithMlRanking.map { currentTab ->
      val defaultValue = if (currentTab == tab) {
        isMlRankingEnabledByDefault
      }
      else {
        originalTabSettingDefaults.getValue(currentTab)
      }
      AdvancedSettingBean().apply {
        id = currentTab.advancedSettingKey
        this.defaultValue = defaultValue.toString()
      }
    }
    ExtensionTestUtil.maskExtensions(AdvancedSettingBean.EP_NAME, settings, testDisposable)
  }

  private fun resetAllRegistryOverrides() {
    (SearchEverywhereMlRegistry.ALL_DISABLE_EXPERIMENT_KEYS + DISABLE_LOGGING_REGISTRY_KEY).forEach {
      Registry.get(it).setValue(false)
    }
  }

  companion object {
    private const val DISABLE_LOGGING_REGISTRY_KEY = "search.everywhere.force.disable.logging.ml"

    @JvmStatic
    fun allTabs(): List<Arguments> {
      return SearchEverywhereTab.tabsWithLogging
        .filterIsInstance<SearchEverywhereTab.TabWithMlRanking>()
        .map { tab ->
          Arguments.of(TabTestCase(tab))
        }
    }
  }
}
