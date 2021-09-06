package com.intellij.ide.actions.searcheverywhere.ml.actions

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereMlSessionService
import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.json.JsonFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class OpenFeaturesInScratchFileAction : AnAction() {
  companion object {
    private const val SHOULD_ORDER_BY_ML_KEY = "shouldOrderByMl"
    private const val CONTEXT_INFO_KEY = "contextInfo"
    private const val FOUND_ELEMENTS_KEY = "foundElements"
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = shouldActionBeEnabled(e)
  }

  private fun shouldActionBeEnabled(e: AnActionEvent): Boolean {
    val seManager = SearchEverywhereManager.getInstance(e.project)
    val session = SearchEverywhereMlSessionService.getService().getCurrentSession()

    return e.project != null
           && seManager.isShown
           && session != null
           && session.isMLSupportedTab(seManager.selectedTabID)
           && session.getCurrentSearchState() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val searchEverywhereUI = SearchEverywhereManager.getInstance(e.project).currentlyShownUI

    val report = getFeaturesReport(searchEverywhereUI)
    val json = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report)

    val context = createScratchFileContext(json)
    val scratchFile = createNewJsonScratchFile(e.project!!, context)
    openScratchFile(scratchFile, e.project!!)
  }

  private fun getFeaturesReport(searchEverywhereUI: SearchEverywhereUI): Map<String, Any> {
    val mlSessionService = SearchEverywhereMlSessionService.getService()
    val searchSession = mlSessionService.getCurrentSession()!!

    val features = searchEverywhereUI.foundElementsInfo.map { info ->
      val id = searchSession.itemIdProvider.getId(info.element)
      val mlWeight = (info.element as? GotoActionModel.MatchedValue)?.let { mlSessionService.getMlWeight(info.contributor, it) }

      ElementFeatures(
        info.element.toString(),
        mlWeight,
        searchSession.getCurrentSearchState()!!.getElementFeatures(id, info.element, info.contributor, info.priority).features
      )
    }

    return mapOf(
      SHOULD_ORDER_BY_ML_KEY to mlSessionService.shouldOrderByMl(),
      CONTEXT_INFO_KEY to searchSession.cachedContextInfo,
      FOUND_ELEMENTS_KEY to features
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

  @JsonPropertyOrder("name", "mlWeight", "features")
  private data class ElementFeatures(val name: String, val mlWeight: Double?, val features: Map<String, Any>)
}