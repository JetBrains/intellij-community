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
class SemanticSearchSettings : PersistentStateComponent<SemanticSearchSettingsState> {
  private var state = SemanticSearchSettingsState()

  var enabledInActionsTab: Boolean
    get() = state.enabledInActionsTab
    set(newValue) {
      state.enabledInActionsTab = newValue
      ActionEmbeddingsStorage.getInstance().run { if (newValue) prepareForSearch() else tryStopGeneratingEmbeddings() }
    }

  override fun getState(): SemanticSearchSettingsState = state

  override fun loadState(newState: SemanticSearchSettingsState) {
    state = newState
  }

  fun getUseRemoteActionsServer() = Registry.`is`("search.everywhere.ml.semantic.actions.server.use")

  fun getActionsAPIToken(): String = Registry.stringValue("search.everywhere.ml.semantic.actions.server.token")

  companion object {
    fun getInstance() = service<SemanticSearchSettings>()
  }
}

class SemanticSearchSettingsState : BaseState() {
  @get:OptionTag("enabled_in_actions_tab")
  var enabledInActionsTab by property(false)
}