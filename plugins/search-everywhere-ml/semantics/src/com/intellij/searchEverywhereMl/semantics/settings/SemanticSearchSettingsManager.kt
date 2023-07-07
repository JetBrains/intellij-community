package com.intellij.searchEverywhereMl.semantics.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.semantics.services.ActionEmbeddingsStorage
import com.intellij.util.xmlb.annotations.OptionTag


@Service(Service.Level.APP)
@State(
  name = "SemanticSearchSettings",
  storages = [Storage(value = "semantic-search-settings.xml", roamingType = RoamingType.DISABLED, exportable = true)]
)
class SemanticSearchSettingsManager : PersistentStateComponent<SemanticSearchSettings> {
  private var state = SemanticSearchSettings()

  override fun getState(): SemanticSearchSettings = state

  override fun loadState(newState: SemanticSearchSettings) {
    state = newState
  }

  fun getIsEnabledInActionsTab() = state.isEnabledInActionsTab

  fun setIsEnabledInActionsTab(newValue: Boolean) {
    state.isEnabledInActionsTab = newValue
    ActionEmbeddingsStorage.getInstance().run { if (newValue) prepareForSearch() else tryStopGeneratingEmbeddings() }
  }

  fun getUseRemoteActionsServer() = Registry.`is`("search.everywhere.ml.semantic.actions.server.use")

  fun getActionsAPIToken(): String = Registry.stringValue("search.everywhere.ml.semantic.actions.server.token")

  companion object {
    fun getInstance() = service<SemanticSearchSettingsManager>()
  }
}

class SemanticSearchSettings : BaseState() {
  @get:OptionTag("enable_in_actions_tab")
  var isEnabledInActionsTab by property(false)
}