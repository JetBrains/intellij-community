package com.intellij.searchEverywhereMl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.searchEverywhereMl.SearchEverywhereMlSettingsService.SettingsState
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/**
 * Service that manages settings for machine learning ranking in Search Everywhere.
 *
 * This service maintains the state of ML ranking settings for different Search Everywhere tabs
 * and synchronizes them with Registry values and experiment configurations.
 *
 * It ensures that:
 * - User preferences are respected and preserved across IDE sessions
 * - Settings visibility and enablement are updated based on current experiment configurations
 * - Registry value changes trigger appropriate updates to the settings state
 * - ML ranking is enabled/disabled appropriately based on experiments and user preferences
 *
 * The service monitors Registry changes through listeners and automatically syncs the internal
 * state when experiment configurations change.
 *
 * It prioritizes user choices over experiment settings, ensuring that if a user explicitly
 * disables ML ranking, it remains disabled regardless of experiment configuration changes.
 *
 * @see SearchEverywhereTab
 * @see SearchEverywhereMlExperiment
 */

@Service(Service.Level.APP)
@State(name="searchEverywhereMlSettings", storages = [Storage("searchEverywhereMlSettings.xml")])
class SearchEverywhereMlSettingsService : SerializablePersistentStateComponent<SettingsState>(SettingsState()),
                                          Disposable {

  /**
   * Initialize the service by:
   * 1. Synchronizing the state with current registry values and experiment settings
   * 2. Setting up listeners for experiment group changes
   * 3. Setting up listeners for registry key changes for each tab
   */
  init {
    sync()
    setExperimentGroupChangeListener()
    setRegistryListeners()
  }

  // region Service Properties
  // Each property and corresponding method is used by the AdvancedSettingsBean.
  // Property name is specified in the plugin.xml, the corresponding methods that
  // control visibility and the enabled/disabled state are accessed by reflection.

  @Suppress("unused")
  var enableActionsTabMlRanking: Boolean
    set(value) {
      updateStateByUser(SearchEverywhereTab.Actions, value)
    }
    get() = state.actionsRankingState.isMlRankingEnabled ?: SearchEverywhereTab.Actions.isMlRankingEnabledByDefault

  @Suppress("unused")
  fun isEnableActionsTabMlRankingEnabled(): Boolean = isSettingEnabledForTab(SearchEverywhereTab.Actions)

  @Suppress("unused")
  fun isEnableActionsTabMlRankingVisible(): Boolean = isSettingVisibleForTab(SearchEverywhereTab.Actions)

  @Suppress("unused")
  var enableFilesTabMlRanking: Boolean
    set(value) {
      updateStateByUser(SearchEverywhereTab.Files, value)
    }
    get() = state.filesRankingState.isMlRankingEnabled ?: SearchEverywhereTab.Files.isMlRankingEnabledByDefault

  @Suppress("unused")
  fun isEnableFilesTabMlRankingEnabled(): Boolean = isSettingEnabledForTab(SearchEverywhereTab.Files)

  @Suppress("unused")
  fun isEnableFilesTabMlRankingVisible(): Boolean = isSettingVisibleForTab(SearchEverywhereTab.Files)

  @Suppress("unused")
  var enableClassesTabMlRanking: Boolean
    set(value) {
      updateStateByUser(SearchEverywhereTab.Classes, value)
    }
    get() = state.classesRankingState.isMlRankingEnabled ?: SearchEverywhereTab.Classes.isMlRankingEnabledByDefault

  @Suppress("unused")
  fun isEnableClassesTabMlRankingEnabled(): Boolean = isSettingEnabledForTab(SearchEverywhereTab.Classes)

  @Suppress("unused")
  fun isEnableClassesTabMlRankingVisible(): Boolean = isSettingVisibleForTab(SearchEverywhereTab.Classes)

  @Suppress("unused")
  var enableAllTabMlRanking: Boolean
    set(value) {
      updateStateByUser(SearchEverywhereTab.All, value)
    }
    get() = state.allRankingState.isMlRankingEnabled ?: SearchEverywhereTab.All.isMlRankingEnabledByDefault

  @Suppress("unused")
  fun isEnableAllTabMlRankingEnabled(): Boolean = isSettingEnabledForTab(SearchEverywhereTab.All)

  @Suppress("unused")
  fun isEnableAllTabMlRankingVisible(): Boolean = isSettingVisibleForTab(SearchEverywhereTab.All)
  // endregion

  /**
   * Updates the ranking state for the specified tab based on user input.
   *
   * @param tab The tab for which the ranking state is to be updated.
   * @param newValue A boolean indicating whether to enable (true) or disable (false) ML ranking for the specified tab.
   */
  private fun updateStateByUser(tab: SearchEverywhereTab.TabWithMlRanking, newValue: Boolean) {
    updateState {
      if (newValue) {
        val state = when (tab.currentExperimentType) {
          SearchEverywhereMlExperiment.ExperimentType.ExperimentalModel -> RankingState.RANKING_WITH_EXPERIMENTAL_MODEL
          else -> RankingState.RANKING_WITH_DEFAULT_MODEL
        }
        it.withTabState(tab, state)
      } else {
        it.withTabState(tab, RankingState.RANKING_DISABLED_BY_USER)
      }
    }
  }

  /**
   * Determines whether the ML ranking setting should be enabled (interactive) for a tab.
   *
   * The setting is enabled when:
   * - The tab has ML ranking enabled by default and there's no active experiment changing this behavior
   * - There's an active experiment that enables ML ranking for this tab
   *
   * @param tab The tab to check
   * @return true if the ML ranking setting should be enabled, false otherwise
   */
  private fun isSettingEnabledForTab(tab: SearchEverywhereTab.TabWithMlRanking): Boolean {
    val currentExperimentType = tab.currentExperimentType

    if (tab.isMlRankingEnabledByDefault) {
      // When a tab has ML ranking enabled by default, we want the user to be able to switch it off
      // only if the behavior is not changed by an experiment

      if (currentExperimentType.isActiveExperiment()) {
        return currentExperimentType.shouldSortByMl
      }

      return true
    }


    // If the current experiment enables ML ranking, we want to enable the option to switch it off
    return currentExperimentType.isActiveExperiment() && currentExperimentType.shouldSortByMl
  }

  /**
   * Determines whether the ML ranking setting should be visible for a tab.
   *
   * The setting is visible when:
   * - The tab has ML ranking enabled by default and there's no active experiment
   * - There's an active experiment that enables ML ranking for this tab
   *
   * @param tab The tab to check
   * @return true if the ML ranking setting should be visible, false otherwise
   */
  private fun isSettingVisibleForTab(tab: SearchEverywhereTab.TabWithMlRanking): Boolean {
    val currentExperiment = tab.currentExperimentType

    if (tab.isMlRankingEnabledByDefault && currentExperiment == SearchEverywhereMlExperiment.ExperimentType.NoExperiment) {
      return true
    }

    if (currentExperiment.isActiveExperiment() && currentExperiment.shouldSortByMl) {
      return true
    }

    return false
  }

  @TestOnly
  internal fun forceSync() {
    sync()
  }

  /**
   * Synchronizes the internal state with current experiment settings and registry values.
   *
   * This method is called during initialization and whenever relevant registry values change.
   * It updates the state for all tabs that support ML ranking.
   */
  private fun sync() {
    SearchEverywhereTab.allTabs
      .filterIsInstance<SearchEverywhereTab.TabWithMlRanking>()
      .onEach { syncTabState(it) }
  }

  /**
   * Synchronizes the state for a specific tab based on current experiment settings and registry values.
   *
   * This method follows a specific decision tree:
   * 1. If the user has explicitly disabled ML ranking, respect that choice and make no changes
   * 2. If experiments are disabled via registry, set appropriate state
   * 3. If there's an active experiment, apply its settings
   * 4. Otherwise, fall back to the tab's default behavior
   *
   * @param tab The tab whose state should be synchronized
   */
  private fun syncTabState(tab: SearchEverywhereTab.TabWithMlRanking) {
    if (state.getTabState(tab) == RankingState.RANKING_DISABLED_BY_USER) {
      // If the user has disabled ranking manually, we do not want to re-enable it for them
      return
    }

    if (SearchEverywhereMlRegistry.isExperimentDisabled(tab)) {
      updateState {
        it.withTabState(tab, RankingState.EXPERIMENTS_DISABLED_BY_REGISTRY)
      }
      return
    }

    val currentExperiment = tab.currentExperimentType
    if (currentExperiment.isActiveExperiment()) {
      updateState {
        val state = if (currentExperiment.shouldSortByMl) RankingState.RANKING_WITH_EXPERIMENTAL_MODEL else RankingState.RANKING_DISABLED_BY_EXPERIMENT
        it.withTabState(tab, state)
      }
    } else {
      updateState {
        val state = if (tab.isMlRankingEnabledByDefault) RankingState.RANKING_WITH_DEFAULT_MODEL else RankingState.DEFAULT_HEURISTICAL_RANKING
        it.withTabState(tab, state)
      }
    }
  }

  private fun setExperimentGroupChangeListener() {
    Registry.get("search.everywhere.ml.experiment.group").addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        sync()
      }
    }, this)
  }

  private fun setRegistryListeners() {
    SearchEverywhereTab.allTabs
      .filterIsInstance<SearchEverywhereTab.TabWithMlRanking>()
      .onEach { registerRegistryValueListenerForTab(it) }
  }

  /**
   * Registers a listener for registry key changes for a specific tab.
   *
   * When the registry key for disabling experiments changes, this listener:
   * 1. Respects user preference if they've manually disabled ML ranking
   * 2. Updates the state to EXPERIMENTS_DISABLED_BY_REGISTRY if experiments are disabled
   * 3. Otherwise, updates the state based on current experiment settings and tab defaults
   *
   * @param tab The tab for which to register the listener
   */
  private fun registerRegistryValueListenerForTab(tab: SearchEverywhereTab.TabWithMlRanking) {
    Registry.get(tab.disableExperimentRegistryKey).addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        if (state.getTabState(tab) == RankingState.RANKING_DISABLED_BY_USER) {
          // We do not want to override the user's choice
          return
        }

        if (value.asBoolean()) {
          // The user has forcefully disabled experiments for this tab through the registry
          updateState {
            it.withTabState(tab, RankingState.EXPERIMENTS_DISABLED_BY_REGISTRY)
          }
          return
        }

        // The experiments have been re-enabled
        // We now need to set everything according to the current experiment type

        val experiment = tab.currentExperimentType
        if (experiment.isActiveExperiment()) {
          val state = if (experiment.shouldSortByMl) RankingState.RANKING_WITH_EXPERIMENTAL_MODEL else RankingState.RANKING_DISABLED_BY_EXPERIMENT
          updateState {
            it.withTabState(tab, state)
          }
        } else {
          val default = tab.isMlRankingEnabledByDefault
          val state = if (default) RankingState.RANKING_WITH_DEFAULT_MODEL else RankingState.DEFAULT_HEURISTICAL_RANKING
          updateState {
            it.withTabState(tab, state)
          }
        }
      }
    }, this)
  }

  override fun dispose() {
  }

  @TestOnly
  internal fun setSettingsState(state: SettingsState) {
    updateState {
      state
    }
  }

  /**
   * Represents the persistent state of ML ranking settings for all Search Everywhere tabs.
   *
   * This class is serialized and stored in XML format. Each tab has its own state,
   * with default values that reflect the initial ranking behavior for that tab.
   *
   * @property allRankingState The ranking state for the "All" tab
   * @property actionsRankingState The ranking state for the "Actions" tab
   * @property classesRankingState The ranking state for the "Classes" tab
   * @property filesRankingState The ranking state for the "Files" tab
   */
  data class SettingsState(
    // The states will change on the first sync
    // The values here are based on the plugin.xml settings
    @JvmField val allRankingState: RankingState = RankingState.DEFAULT_HEURISTICAL_RANKING,
    @JvmField val actionsRankingState: RankingState = RankingState.RANKING_WITH_DEFAULT_MODEL,
    @JvmField val classesRankingState: RankingState = RankingState.RANKING_WITH_DEFAULT_MODEL,
    @JvmField val filesRankingState: RankingState = RankingState.RANKING_WITH_DEFAULT_MODEL,
  )

  private fun SettingsState.getTabState(tab: SearchEverywhereTab.TabWithMlRanking): RankingState {
    return when (tab) {
      SearchEverywhereTab.Actions -> actionsRankingState
      SearchEverywhereTab.All -> allRankingState
      SearchEverywhereTab.Classes -> classesRankingState
      SearchEverywhereTab.Files -> filesRankingState
    }
  }

  /**
   * Represents the possible states of ML ranking for a Search Everywhere tab.
   *
   * The `isMlRankingEnabled` property determines whether ML ranking is enabled in this state.
   * A null value means the decision should be deferred to other settings (e.g., default tab configuration).
   *
   * States:
   * - RANKING_WITH_DEFAULT_MODEL: ML ranking is enabled with the default model
   * - RANKING_WITH_EXPERIMENTAL_MODEL: ML ranking is enabled with an experimental model
   * - RANKING_DISABLED_BY_EXPERIMENT: ML ranking is disabled by an experiment
   * - RANKING_DISABLED_BY_USER: ML ranking was explicitly disabled by the user
   * - EXPERIMENTS_DISABLED_BY_REGISTRY: Experiments are disabled via registry key
   * - DEFAULT_HEURISTICAL_RANKING: ML ranking is not enabled, using default heuristic ranking
   */
  enum class RankingState(val isMlRankingEnabled: Boolean?) {
    RANKING_WITH_DEFAULT_MODEL(true),
    RANKING_WITH_EXPERIMENTAL_MODEL(true),

    RANKING_DISABLED_BY_EXPERIMENT(false),
    RANKING_DISABLED_BY_USER(false),
    EXPERIMENTS_DISABLED_BY_REGISTRY(null),

    DEFAULT_HEURISTICAL_RANKING(false)
  }
}

/**
 * Extension function that creates a new SettingsState with the ranking state updated for a specific tab.
 *
 * This function maintains immutability by creating a copy of the original state with just one field changed.
 *
 * @param tab The tab whose state should be updated
 * @param state The new ranking state for the tab
 * @return A new SettingsState with the updated ranking state for the specified tab
 */
@VisibleForTesting
fun SettingsState.withTabState(tab: SearchEverywhereTab.TabWithMlRanking, state: SearchEverywhereMlSettingsService.RankingState) : SettingsState {
  thisLogger().debug("Changing the state of $tab to $state")
  return when (tab) {
    is SearchEverywhereTab.Actions -> copy(actionsRankingState = state)
    is SearchEverywhereTab.All -> copy(allRankingState = state)
    is SearchEverywhereTab.Classes -> copy(classesRankingState = state)
    is SearchEverywhereTab.Files -> copy(filesRankingState = state)
  }
}
