package com.intellij.searchEverywhereMl.settings

import com.intellij.openapi.components.*
import com.intellij.searchEverywhereMl.settings.SearchEverywhereMlExperimentSettingState.*
import com.intellij.util.xmlb.annotations.OptionTag

@Service(Service.Level.APP)
@State(
  name = "SearchEverywhereMlSettings",
  storages = [Storage(value = "semantic-search-settings.xml", roamingType = RoamingType.DISABLED, exportable = true)]
)
class SearchEverywhereMlSettingsStorage: PersistentStateComponent<SearchEverywhereMlSettingsState> {
  private var state = SearchEverywhereMlSettingsState()

  companion object {
    fun getInstance(): SearchEverywhereMlSettingsStorage = service()
  }

  var enableMlRankingInAll: Boolean
    get() = state.mlRankingInAllTabEnabledState
    set(newValue) {
      state.mlRankingInAllTabSettingState = CHANGED_MANUALLY
      state.mlRankingInAllTabEnabledState = newValue
    }

  val enabledMlRankingInAllDefaultState: Boolean
    get() = state.mlRankingInAllTabDefaultState

  fun updateExperimentStateInAllTabIfAllowed(mlRankingEnabledByExperiment: Boolean): Boolean {
    if (state.mlRankingInAllTabSettingState == CHANGED_MANUALLY) {
      return false
    }

    state.mlRankingInAllTabSettingState = CHANGED_BY_EXPERIMENT
    state.mlRankingInAllTabEnabledState = mlRankingEnabledByExperiment
    return true
  }

  fun disableExperimentInAllTab() {
    if (state.mlRankingInAllTabSettingState != CHANGED_MANUALLY) {
      state.mlRankingInAllTabSettingState = DEFAULT
      state.mlRankingInAllTabEnabledState = state.mlRankingInAllTabDefaultState
    }
  }

  override fun getState(): SearchEverywhereMlSettingsState = state

  override fun loadState(newState: SearchEverywhereMlSettingsState) {
    state = newState
  }
}

enum class SearchEverywhereMlExperimentSettingState {
  CHANGED_MANUALLY,
  CHANGED_BY_EXPERIMENT,
  DEFAULT
}

class SearchEverywhereMlSettingsState : BaseState() {
  @get:OptionTag("ml_ranking_in_all_tab_experiment_state")
  var mlRankingInAllTabSettingState by enum(DEFAULT)

  @get:OptionTag("ml_ranking_in_all_tab_enabled_state")
  var mlRankingInAllTabEnabledState by property(false)

  @get:OptionTag("ml_ranking_in_all_tab_default_state")
  var mlRankingInAllTabDefaultState by property(false)
}