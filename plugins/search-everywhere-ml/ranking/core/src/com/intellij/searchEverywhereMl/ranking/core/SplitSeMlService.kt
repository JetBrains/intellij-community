package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopesStateService
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.frontend.ml.SeMlService
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilter
import com.intellij.platform.searchEverywhere.providers.target.SeTargetsFilter
import com.intellij.platform.searchEverywhere.withWeight
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchResultAdapter
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchStateChangeReason

internal class SplitSeMlService : SeMlService {
  override val isEnabled: Boolean
    get() = SearchEverywhereMlFacade.isMlEnabled


  override fun onSessionStarted(project: Project?, tabId: String) {
    SearchEverywhereMlFacade.onSessionStarted(project, tabId, isNewSearchEverywhere = true,
                                              providersInfo = SearchResultProvidersInfo.forSplitSession())
  }

  override fun applyMlWeight(seItemData: SeItemData): SeItemData {
    val adapter = SearchResultAdapter.createAdapterFor(seItemData)
    val processedSearchResult = SearchEverywhereMlFacade.processSearchResult(adapter)

    if (processedSearchResult.mlProbability != null) {
      return seItemData.withWeight(processedSearchResult.finalPriority)
    }
    else {
      return seItemData
    }
  }

  override fun notifySearchResultsUpdated() {
    SearchEverywhereMlFacade.notifySearchResultsUpdated()
  }

  override fun onStateStarted(tabId: String, searchParams: SeParams) {
    val activeSession = checkNotNull(SearchEverywhereMlFacade.activeSession) { "Cannot call onStateStarted without active search session" }
    val project = activeSession.project
    val currentProject = currentOrDefaultProject(project)
    val isDumbMode = project?.let { DumbService.isDumb(it) } ?: false

    val scopeDescriptor = searchParams.getScopeDescriptorIfExists(currentProject)
    val isSearchEverywhere = searchParams.isSearchEverywhere()
    val previousState = activeSession.activeState ?: activeSession.previousSearchState
    val reason = SearchStateChangeReason.inferFromStateChange(previousState, tabId, searchParams, isDumbMode)

    SearchEverywhereMlFacade.onStateStarted(tabId, searchParams.inputQuery, reason, scopeDescriptor, isSearchEverywhere,
                                            searchFilter = searchParams.filter, isDumbMode = isDumbMode)
  }

  override fun onStateFinished(results: List<SeItemData>) {
    SearchEverywhereMlFacade.onStateFinished(results.map { SearchResultAdapter.createAdapterFor(it) })
  }

  override fun onResultsSelected(selectedResults: List<Pair<Int, SeItemData>>) {
    SearchEverywhereMlFacade.onResultsSelected(
      selectedResults.map {
        it.first to SearchResultAdapter.createAdapterFor(it.second)
      }
    )
  }

  override fun onSessionFinished() {
    SearchEverywhereMlFacade.onSessionFinished()
  }

  private fun SeParams.getScopeDescriptorIfExists(project: Project): ScopeDescriptor? {
    val selectedScopeId = SeTargetsFilter.from(this.filter).selectedScopeId ?: return null
    return ScopesStateService.getInstance(project)
      .getScopesState()
      .getScopeDescriptorById(selectedScopeId)
  }

  private fun SeParams.isSearchEverywhere(): Boolean {
    return SeEverywhereFilter.isEverywhere(this.filter) ?: false
  }
}
