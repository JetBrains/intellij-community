// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMlService
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class SearchEverywhereMlSessionService : SearchEverywhereMlService() {
  companion object {
    internal const val RECORDER_CODE = "MLSE"

    fun getService() = EP_NAME.findExtensionOrFail(SearchEverywhereMlSessionService::class.java)
  }

  private val sessionIdCounter = AtomicInteger()
  private var activeSession: AtomicReference<SearchEverywhereMLSearchSession?> = AtomicReference()

  private val experiment: SearchEverywhereMlExperiment = SearchEverywhereMlExperiment()

  override fun shouldOrderByMl(): Boolean = experiment.shouldOrderByMl()

  override fun getMlWeight(contributor: SearchEverywhereContributor<*>, element: GotoActionModel.MatchedValue): Double {
    val session = getCurrentSession() ?: return -1.0
    return session.getMLWeight(contributor, element)
  }

  fun getCurrentSession(): SearchEverywhereMLSearchSession? {
    if (experiment.isAllowed) {
      return activeSession.get()
    }
    return null
  }

  override fun onSessionStarted(project: Project?) {
    if (experiment.isAllowed) {
      activeSession.updateAndGet { SearchEverywhereMLSearchSession(project, sessionIdCounter.incrementAndGet()) }
    }
  }

  override fun onSearchRestart(project: Project?,
                               tabId: String,
                               reason: SearchRestartReason,
                               keysTyped: Int,
                               backspacesTyped: Int,
                               textLength: Int,
                               previousElementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    if (experiment.isAllowed) {
      getCurrentSession()?.onSearchRestart(project, previousElementsProvider, reason, tabId, keysTyped, backspacesTyped, textLength)
    }
  }

  override fun onItemSelected(project: Project?, indexes: IntArray, selectedItems: List<Any>, closePopup: Boolean,
                              elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    if (experiment.isAllowed) {
      getCurrentSession()?.onItemSelected(project, experiment, indexes, selectedItems, closePopup, elementsProvider)
    }
  }

  override fun onSearchFinished(project: Project?, elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    if (experiment.isAllowed) {
      getCurrentSession()?.onSearchFinished(project, experiment, elementsProvider)
    }
  }

  override fun onDialogClose() {
    activeSession.updateAndGet { null }
  }
}