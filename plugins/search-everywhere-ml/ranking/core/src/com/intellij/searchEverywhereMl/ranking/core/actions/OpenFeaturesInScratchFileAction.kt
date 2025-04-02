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
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereFoundElementInfoWithMl
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereRankingDiffCalculator
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereContributorFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.core.searchEverywhereMlRankingService

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
    private const val RANKING_DIFFS_KEY = "rankingDiffs"
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
    val session = searchEverywhereMlRankingService?.getCurrentSession()

    return e.project != null
           && seManager.isShown
           && session != null
           && session.getCurrentSearchState() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val searchEverywhereUI = SearchEverywhereManager.getInstance(e.project).currentlyShownPopupInstance as? SearchEverywhereUI
                             ?: error("SearchEverywhereUI.getCurrentlyShownUI() is deprecated. " +
                                      "The functionality is not yet supported for SearchEverywherePopupInstance")

    val report = getFeaturesReport(searchEverywhereUI)
    val json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report)

    val context = createScratchFileContext(json)
    val scratchFile = createNewJsonScratchFile(e.project!!, context)
    openScratchFile(scratchFile, e.project!!)
  }

  private fun getFeaturesReport(searchEverywhereUI: SearchEverywhereUI): Map<String, Any> {
    val mlSessionService = searchEverywhereMlRankingService ?: return emptyMap()
    val searchSession = mlSessionService.getCurrentSession()!!
    val state = searchSession.getCurrentSearchState()!!

    val foundElementsInfo = searchEverywhereUI.foundElementsInfo

    val features = foundElementsInfo
      .map { SearchEverywhereFoundElementInfoWithMl.from(it) }
      .map { info ->
        val rankingWeight = info.priority
        val contributor = info.contributor.searchProviderId
        val elementName = StringUtil.notNullize(info.element.toString(), "undefined")
        val mlWeight = info.mlWeight
        val mlFeatures: Map<String, Any> = info.mlFeatures.associate { it.field.name to it.data as Any }

        val elementId = ReadAction.compute<Int?, Nothing> { searchSession.itemIdProvider.getId(info.element) }
        return@map ElementFeatures(
          elementId,
          elementName,
          mlWeight,
          rankingWeight,
          contributor,
          mlFeatures.toSortedMap()
        )
      }

    val contributors = foundElementsInfo.map { info -> info.contributor }.toHashSet()
    val contributorFeaturesProvider = SearchEverywhereContributorFeaturesProvider()
    val contributorFeatures = contributors.map { contributorFeaturesProvider.getFeatures(it, searchSession.mixedListInfo,
                                                                                         searchSession.sessionStartTime)}

    val diffInfos = if (state.orderByMl) SearchEverywhereRankingDiffCalculator.getRankingDiffInfos(foundElementsInfo) else emptyList()

    return mapOf(
      SHOULD_ORDER_BY_ML_KEY to state.orderByMl,
      EXPERIMENT_GROUP_KEY to SearchEverywhereMlExperiment.experimentGroup,
      CONTEXT_INFO_KEY to searchSession.cachedContextInfo.features.associate { it.field.name to it.data },
      SEARCH_STATE_FEATURES_KEY to state.searchStateFeatures.associate { it.field.name to it.data },
      CONTRIBUTORS_KEY to contributorFeatures.map { c -> c.associate { it.field.name to it.data }.toSortedMap() },
      FOUND_ELEMENTS_KEY to features,
      RANKING_DIFFS_KEY to diffInfos
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

  @JsonPropertyOrder("id", "weight")
  private data class ContributorInfo(val id: String, val weight: Int)
}