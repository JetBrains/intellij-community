package com.intellij.searchEverywhereMl.ranking.core.actions

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.json.JsonFileType
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMlFacade
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchResultAdapter
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchResultProviderAdapter
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereContributorFeaturesProvider

/**
 * This action will open a scratch file with a feature dump.
 * To use it, you must have Search Everywhere opened, then use the shortcut associated with this action
 * (Shift+Ctrl+Alt+1 on Windows, Shift+Option+Command+1 on MacOS).
 */
class OpenFeaturesInScratchFileAction : AnAction() {
  companion object {
    private const val SHOULD_ORDER_BY_ML_KEY = "shouldOrderByMl"
    private const val EXPERIMENT_GROUP_KEY = "experimentGroup"
    private const val CONTEXT_INFO_KEY = "contextInfo"
    private const val SEARCH_STATE_FEATURES_KEY = "searchStateFeatures"
    private const val CONTRIBUTORS_KEY = "contributors"
    private const val FOUND_ELEMENTS_KEY = "foundElements"
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = shouldActionBeEnabled(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  private fun shouldActionBeEnabled(e: AnActionEvent): Boolean {
    // Disable in Search, as otherwise we cannot run it because the session is over. See IDEA-321125
    if (e.place == ActionPlaces.ACTION_SEARCH) return false

    val seManager = SearchEverywhereManager.getInstance(e.project)
    val session = SearchEverywhereMlFacade.activeSession

    return e.project != null
           && seManager.isShown
           && session != null
           && session.activeState != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val searchEverywhereUI = SearchEverywhereManager.getInstance(e.project).currentlyShownPopupInstance as? SearchEverywhereUI
                             ?: throw UnsupportedOperationException("SearchEverywhereUI.getCurrentlyShownUI() is deprecated. " +
                                                                    "The functionality is not yet supported for SearchEverywherePopupInstance")

    val report = getFeaturesReport(searchEverywhereUI)
    val json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report)

    val context = createScratchFileContext(json)
    val scratchFile = createNewJsonScratchFile(e.project!!, context)
    openScratchFile(scratchFile, e.project!!)
  }

  private fun getFeaturesReport(searchEverywhereUI: SearchEverywhereUI): Map<String, Any> {
    val searchSession = SearchEverywhereMlFacade.activeSession!!
    val state = searchSession.activeState!!

    val foundElementsInfo = searchEverywhereUI.foundElementsInfo

    val features = foundElementsInfo
      .map { SearchResultAdapter.createAdapterFor(it) }
      .map { state.getProcessedSearchResultById(it.stateLocalId) }
      .map { searchResult ->
        val rankingWeight = searchResult.originalWeight
        val contributor = searchResult.provider.id
        val elementName = searchResult.rawItem.toString()
        val mlWeight = searchResult.mlProbability?.value
        val mlFeatures = searchResult.mlFeatures?.associate { it.field.name to it.data as Any } ?: emptyMap()
        val elementId = searchResult.sessionWideId?.value

        return@map ElementFeatures(
          elementId,
          elementName,
          mlWeight,
          rankingWeight,
          contributor,
          mlFeatures.toSortedMap()
        )
      }

    val providers = foundElementsInfo
      .map { info -> info.contributor }
      .map { SearchResultProviderAdapter.createAdapterFor(it) }
      .toSet()

    val contributorFeatures = providers.map { SearchEverywhereContributorFeaturesProvider.getFeatures(it,
                                                                                                      searchSession.sessionStartTime)}

    return mapOf(
      SHOULD_ORDER_BY_ML_KEY to state.orderByMl,
      EXPERIMENT_GROUP_KEY to SearchEverywhereMlExperiment.experimentGroup,
      CONTEXT_INFO_KEY to searchSession.cachedContextInfo.features.associate { it.field.name to it.data },
      SEARCH_STATE_FEATURES_KEY to state.searchStateFeatures.associate { it.field.name to it.data },
      CONTRIBUTORS_KEY to contributorFeatures.map { c -> c.associate { it.field.name to it.data }.toSortedMap() },
      FOUND_ELEMENTS_KEY to features,
    )
  }

  private fun createScratchFileContext(json: String) = ScratchFileCreationHelper.Context().apply {
    text = json
    fileExtension = JsonFileType.DEFAULT_EXTENSION
    createOption = ScratchFileService.Option.create_if_missing
  }

  private fun createNewJsonScratchFile(project: Project, context: ScratchFileCreationHelper.Context): VirtualFile {
    val fileName = "search-everywhere-features.${context.fileExtension}"
    return ScratchRootType.getInstance().createScratchFile(project, fileName, context.language, context.text, context.createOption)!!
  }

  private fun openScratchFile(file: VirtualFile, project: Project) {
    FileEditorManager.getInstance(project).openFile(file, true)
  }

  @JsonPropertyOrder("id", "name", "mlWeight", "rankingWeight", "contributor", "features")
  private data class ElementFeatures(val id: Int?,
                                     val name: String,
                                     val mlWeight: Double?,
                                     val rankingWeight: Int,
                                     val contributor: String,
                                     val features: Map<String, Any>)

}