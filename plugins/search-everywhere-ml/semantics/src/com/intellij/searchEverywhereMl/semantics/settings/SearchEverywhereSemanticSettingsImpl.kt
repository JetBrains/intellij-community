package com.intellij.searchEverywhereMl.semantics.settings

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.embeddings.indexer.FileBasedEmbeddingIndexer
import com.intellij.platform.ml.embeddings.actions.ActionEmbeddingStorageManager
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment.ExperimentType.ENABLE_SEMANTIC_SEARCH
import com.intellij.searchEverywhereMl.SearchEverywhereTabWithMlRanking
import com.intellij.util.xmlb.annotations.OptionTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Service(Service.Level.APP)
@State(
  name = "SemanticSearchSettings",
  storages = [Storage(value = "semantic-search-settings.xml", roamingType = RoamingType.DISABLED, exportable = true)]
)
class SearchEverywhereSemanticSettingsImpl : SearchEverywhereSemanticSettingsBase()

abstract class SearchEverywhereSemanticSettingsBase : SearchEverywhereSemanticSettings,
                                                      PersistentStateComponent<SearchEverywhereSemanticSettingsState> {
  private var state = SearchEverywhereSemanticSettingsState()

  private val isEAP by lazy { ApplicationManager.getApplication().isEAP }

  protected val enabledInClassesTabFlow by lazy { MutableStateFlow(enabledInClassesTab) }
  protected val enabledInSymbolsTabFlow by lazy { MutableStateFlow(enabledInSymbolsTab) }

  override fun getEnabledInClassesTabState(): StateFlow<Boolean> = enabledInClassesTabFlow.asStateFlow()
  override fun getEnabledInSymbolsTabState(): StateFlow<Boolean> = enabledInSymbolsTabFlow.asStateFlow()

  override var enabledInActionsTab: Boolean
    get() {
      if (state.actionsTabManuallySet) {
        return state.manualEnabledInActionsTab
      }
      val providerId = ActionSearchEverywhereContributor::class.java.simpleName
      return AdvancedSettings.getDefaultBoolean("search.everywhere.ml.semantic.actions.enable") ||
             (isEAP && getExperimentType(providerId) == ENABLE_SEMANTIC_SEARCH)
    }
    set(newValue) {
      state.actionsTabManuallySet = true
      state.manualEnabledInActionsTab = newValue
      if (newValue) {
        ActionEmbeddingStorageManager.getInstance().prepareForSearch()
      }
    }

  override var enabledInFilesTab: Boolean
    get() {
      if (state.filesTabManuallySet) {
        return state.manualEnabledInFilesTab
      }
      val providerId = FileSearchEverywhereContributor::class.java.simpleName
      return AdvancedSettings.getDefaultBoolean("search.everywhere.ml.semantic.files.enable") ||
             (isEAP && getExperimentType(providerId) == ENABLE_SEMANTIC_SEARCH)
    }
    set(newValue) {
      state.filesTabManuallySet = true
      state.manualEnabledInFilesTab = newValue
      if (newValue) {
        ProjectManager.getInstance().openProjects.forEach { FileBasedEmbeddingIndexer.getInstance().prepareForSearch(it) }
      }
    }

  open fun getDefaultClassesEnabled(): Boolean {
    val providerId = ClassSearchEverywhereContributor::class.java.simpleName
    return AdvancedSettings.getDefaultBoolean("search.everywhere.ml.semantic.classes.enable") ||
           (isEAP && getExperimentType(providerId) == ENABLE_SEMANTIC_SEARCH)
  }

  override var enabledInClassesTab: Boolean
    get() {
      if (state.classesTabManuallySet) {
        return state.manualEnabledInClassesTab
      }
      return getDefaultClassesEnabled()
    }
    set(newValue) {
      state.classesTabManuallySet = true
      state.manualEnabledInClassesTab = newValue
      if (newValue) {
        ProjectManager.getInstance().openProjects.forEach { FileBasedEmbeddingIndexer.getInstance().prepareForSearch(it) }
      }
      enabledInClassesTabFlow.value = newValue
    }

  open fun getDefaultSymbolsEnabled(): Boolean {
    val providerId = SymbolSearchEverywhereContributor::class.java.simpleName
    return AdvancedSettings.getDefaultBoolean("search.everywhere.ml.semantic.symbols.enable") ||
           (isEAP && getExperimentType(providerId) == ENABLE_SEMANTIC_SEARCH)
  }

  private fun getExperimentType(providerId: String): SearchEverywhereMlExperiment.ExperimentType {
    return SearchEverywhereMlExperiment().getExperimentForTab(SearchEverywhereTabWithMlRanking.findById(providerId)!!)
  }

  override var enabledInSymbolsTab: Boolean
    get() {
      if (state.symbolsTabManuallySet) {
        return state.manualEnabledInSymbolsTab
      }
      return getDefaultSymbolsEnabled()
    }
    set(newValue) {
      state.symbolsTabManuallySet = true
      state.manualEnabledInSymbolsTab = newValue
      if (newValue) {
        ProjectManager.getInstance().openProjects.forEach { FileBasedEmbeddingIndexer.getInstance().prepareForSearch(it) }
      }
      enabledInSymbolsTabFlow.value = newValue
    }

  override fun isEnabled(): Boolean = enabledInActionsTab || enabledInFilesTab || enabledInSymbolsTab || enabledInClassesTab

  override fun getUseRemoteActionsServer() = Registry.`is`("search.everywhere.ml.semantic.actions.server.use")

  override fun getActionsAPIToken() = Registry.stringValue("search.everywhere.ml.semantic.actions.server.token")

  override fun getState(): SearchEverywhereSemanticSettingsState = state

  override fun loadState(newState: SearchEverywhereSemanticSettingsState) {
    state = newState
  }

  companion object {
    fun getInstance(): SearchEverywhereSemanticSettings = service()
  }
}

// Default values should never be changed here because otherwise the absence of value in storage can be interpreted differently
class SearchEverywhereSemanticSettingsState : BaseState() {
  @get:OptionTag("manual_enabled_in_actions_tab")
  var manualEnabledInActionsTab by property(false)

  @get:OptionTag("actions_tab_manually_set")
  var actionsTabManuallySet by property(false)

  @get:OptionTag("manual_enabled_in_files_tab")
  var manualEnabledInFilesTab by property(false)

  @get:OptionTag("files_tab_manually_set")
  var filesTabManuallySet by property(false)

  @get:OptionTag("manual_enabled_in_classes_tab")
  var manualEnabledInClassesTab by property(false)

  @get:OptionTag("classes_tab_manually_set")
  var classesTabManuallySet by property(false)

  @get:OptionTag("manual_enabled_in_symbols_tab")
  var manualEnabledInSymbolsTab by property(false)

  @get:OptionTag("symbols_tab_manually_set")
  var symbolsTabManuallySet by property(false)
}