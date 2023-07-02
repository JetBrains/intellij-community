package com.intellij.searchEverywhereMl.semantics.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.*
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
    if (newValue) {
      service<ActionEmbeddingsStorage>().prepareForSearch()
    } else {
      service<ActionEmbeddingsStorage>().tryStopGeneratingEmbeddings()
    }
  }

  fun getUseRemoteActionsServer() = state.actionsUseRemoteServer

  fun getActionsAPIToken(): String {
    return PasswordSafe.instance.get(actionsAPITokenAttributes)?.getPasswordAsString() ?: ""
  }

  fun setActionsAPIToken(token: String) {
    PasswordSafe.instance.set(actionsAPITokenAttributes, Credentials("default", token))
  }

  companion object {
    private const val SEMANTIC_SEARCH = "SEMANTIC_SEARCH"
    private const val ACTIONS_API_TOKEN = "ACTIONS_API_TOKEN"

    private val actionsAPITokenAttributes = CredentialAttributes(generateServiceName(SEMANTIC_SEARCH, ACTIONS_API_TOKEN))
  }
}

class SemanticSearchSettings : BaseState() {
  @get:OptionTag("enable_in_actions_tab")
  var isEnabledInActionsTab by property(false)

  @get:OptionTag("actions_use_remote_server")
  var actionsUseRemoteServer by property(false)
}