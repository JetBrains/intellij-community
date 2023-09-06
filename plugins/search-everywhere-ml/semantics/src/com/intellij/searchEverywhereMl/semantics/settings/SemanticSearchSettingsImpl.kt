package com.intellij.searchEverywhereMl.semantics.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.semantics.services.ActionEmbeddingsStorage
import com.intellij.searchEverywhereMl.semantics.services.ClassEmbeddingsStorage
import com.intellij.searchEverywhereMl.semantics.services.FileEmbeddingsStorage
import com.intellij.searchEverywhereMl.semantics.services.SymbolEmbeddingStorage
import com.intellij.util.xmlb.annotations.OptionTag


@Service(Service.Level.APP)
@State(
  name = "SemanticSearchSettings",
  storages = [Storage(value = "semantic-search-settings.xml", roamingType = RoamingType.DISABLED, exportable = true)]
)
class SemanticSearchSettingsImpl : SemanticSearchSettings, PersistentStateComponent<SemanticSearchSettingsState> {
  private var state = SemanticSearchSettingsState()

  override var enabledInActionsTab: Boolean
    get() = state.enabledInActionsTab
    set(newValue) {
      state.enabledInActionsTab = newValue
      ProjectManager.getInstance().openProjects.first().let {
        ActionEmbeddingsStorage.getInstance().run { if (newValue) prepareForSearch(it) else tryStopGeneratingEmbeddings() }
      }
    }

  override var enabledInFilesTab: Boolean
    get() = state.enabledInFilesTab
    set(newValue) {
      state.enabledInFilesTab = newValue
      ProjectManager.getInstance().openProjects.forEach {
        FileEmbeddingsStorage.getInstance(it).run { if (newValue) prepareForSearch() else tryStopGeneratingEmbeddings()}
      }
    }

  override var enabledInSymbolsTab: Boolean
    get() = state.enabledInSymbolsTab
    set(newValue) {
      state.enabledInSymbolsTab = newValue
      ProjectManager.getInstance().openProjects.forEach {
        SymbolEmbeddingStorage.getInstance(it).run { if (newValue) prepareForSearch() else tryStopGeneratingEmbeddings() }
      }
    }

  override var enabledInClassesTab: Boolean
    get() = state.enabledInClassesTab
    set(newValue) {
      state.enabledInClassesTab = newValue
      ProjectManager.getInstance().openProjects.forEach {
        ClassEmbeddingsStorage.getInstance(it).run { if (newValue) prepareForSearch() else tryStopGeneratingEmbeddings() }
      }
    }

  override fun getState(): SemanticSearchSettingsState = state

  override fun loadState(newState: SemanticSearchSettingsState) {
    state = newState
  }

  override fun isEnabled() = enabledInActionsTab || enabledInFilesTab || enabledInSymbolsTab || enabledInClassesTab

  override fun getUseRemoteActionsServer() = Registry.`is`("search.everywhere.ml.semantic.actions.server.use")

  override fun getActionsAPIToken(): String = Registry.stringValue("search.everywhere.ml.semantic.actions.server.token")

  companion object {
    fun getInstance() = service<SemanticSearchSettings>()
  }
}

class SemanticSearchSettingsState : BaseState() {
  @get:OptionTag("enabled_in_actions_tab")
  var enabledInActionsTab by property(false)

  @get:OptionTag("enabled_in_files_tab")
  var enabledInFilesTab by property(false)

  @get:OptionTag("enabled_in_symbols_tab")
  var enabledInSymbolsTab by property(false)

  @get:OptionTag("enabled_in_classes_tab")
  var enabledInClassesTab by property(false)
}