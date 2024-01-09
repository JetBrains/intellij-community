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
import com.intellij.platform.ml.embeddings.search.services.*
import com.intellij.searchEverywhereMl.semantics.experiments.SearchEverywhereSemanticExperiments
import com.intellij.searchEverywhereMl.semantics.experiments.SearchEverywhereSemanticExperiments.SemanticSearchFeature.ENABLED
import com.intellij.util.xmlb.annotations.OptionTag

@Service(Service.Level.APP)
@State(
  name = "SemanticSearchSettings",
  storages = [Storage(value = "semantic-search-settings.xml", roamingType = RoamingType.DISABLED, exportable = true)]
)
class SearchEverywhereSemanticSettingsImpl : SearchEverywhereSemanticSettings,
                                             PersistentStateComponent<SearchEverywhereSemanticSettingsState> {
  private var state = SearchEverywhereSemanticSettingsState()

  private val isInternal by lazy { ApplicationManager.getApplication().isInternal }
  private val isEAP by lazy { ApplicationManager.getApplication().isEAP }

  override var enabledInActionsTab: Boolean
    get() {
      if (state.actionsTabManuallySet) {
        return state.manualEnabledInActionsTab
      }
      val providerId = ActionSearchEverywhereContributor::class.java.simpleName
      return AdvancedSettings.getDefaultBoolean("search.everywhere.ml.semantic.actions.enable") ||
             isInternal || (isEAP && SearchEverywhereSemanticExperiments.getInstance().getSemanticFeatureForTab(providerId) == ENABLED)
    }
    set(newValue) {
      state.actionsTabManuallySet = true
      state.manualEnabledInActionsTab = newValue
      ProjectManager.getInstance().openProjects.firstOrNull()?.let {
        ActionEmbeddingsStorage.getInstance().run { if (newValue) prepareForSearch(it) else tryStopGeneratingEmbeddings() }
      }
    }

  override var enabledInFilesTab: Boolean
    get() {
      if (state.filesTabManuallySet) {
        return state.manualEnabledInFilesTab
      }
      val providerId = FileSearchEverywhereContributor::class.java.simpleName
      return AdvancedSettings.getDefaultBoolean("search.everywhere.ml.semantic.files.enable") ||
             (isEAP && SearchEverywhereSemanticExperiments.getInstance().getSemanticFeatureForTab(providerId) == ENABLED)
    }
    set(newValue) {
      state.filesTabManuallySet = true
      state.manualEnabledInFilesTab = newValue
      ProjectManager.getInstance().openProjects.forEach {
        FileEmbeddingsStorage.getInstance(it).run { if (newValue) prepareForSearch() else tryStopGeneratingEmbeddings() }
      }
    }

  override var enabledInClassesTab: Boolean
    get() {
      if (state.classesTabManuallySet) {
        return state.manualEnabledInClassesTab
      }
      val providerId = ClassSearchEverywhereContributor::class.java.simpleName
      return AdvancedSettings.getDefaultBoolean("search.everywhere.ml.semantic.classes.enable") ||
             (isEAP && SearchEverywhereSemanticExperiments.getInstance().getSemanticFeatureForTab(providerId) == ENABLED)
    }
    set(newValue) {
      state.classesTabManuallySet = true
      state.manualEnabledInClassesTab = newValue
      ProjectManager.getInstance().openProjects.forEach {
        ClassEmbeddingsStorage.getInstance(it).run { if (newValue) prepareForSearch() else tryStopGeneratingEmbeddings() }
      }
    }

  override var enabledInSymbolsTab: Boolean
    get() {
      if (state.symbolsTabManuallySet) {
        return state.manualEnabledInSymbolsTab
      }
      val providerId = SymbolSearchEverywhereContributor::class.java.simpleName
      return AdvancedSettings.getDefaultBoolean("search.everywhere.ml.semantic.symbols.enable") ||
             (isEAP && SearchEverywhereSemanticExperiments.getInstance().getSemanticFeatureForTab(providerId) == ENABLED)
    }
    set(newValue) {
      state.symbolsTabManuallySet = true
      state.manualEnabledInSymbolsTab = newValue
      ProjectManager.getInstance().openProjects.forEach {
        SymbolEmbeddingStorage.getInstance(it).run { if (newValue) prepareForSearch() else tryStopGeneratingEmbeddings() }
      }
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