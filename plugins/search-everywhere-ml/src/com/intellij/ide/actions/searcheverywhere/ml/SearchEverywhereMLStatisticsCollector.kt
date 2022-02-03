// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereMlSessionService.Companion.RECORDER_CODE
import com.intellij.ide.actions.searcheverywhere.ml.id.SearchEverywhereMlItemIdProvider
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.NonUrgentExecutor
import kotlin.math.round

internal class SearchEverywhereMLStatisticsCollector {
  private val loggerProvider = StatisticsEventLogProviderUtil.getEventLogProvider(RECORDER_CODE)

  fun onItemSelected(project: Project?, seSessionId: Int, searchIndex: Int,
                     experimentGroup: Int, orderByMl: Boolean,
                     elementIdProvider: SearchEverywhereMlItemIdProvider,
                     context: SearchEverywhereMLContextInfo,
                     cache: SearchEverywhereMlSearchState,
                     selectedIndices: IntArray,
                     selectedItems: List<Any>,
                     closePopup: Boolean,
                     timeToFirstResult: Int,
                     elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    val experimentFromRegistry = Registry.intValue("search.everywhere.ml.experiment.group") >= 0
    val data = arrayListOf<Pair<String, Any>>(
      CLOSE_POPUP_KEY to closePopup,
      FORCE_EXPERIMENT_GROUP to experimentFromRegistry
    )
    reportElements(
      project, SESSION_FINISHED, seSessionId, searchIndex, experimentGroup, orderByMl,
      elementIdProvider, context, cache, timeToFirstResult, data,
      selectedIndices, selectedItems, elementsProvider
    )
  }

  fun onSearchFinished(project: Project?, seSessionId: Int, searchIndex: Int,
                       experimentGroup: Int, orderByMl: Boolean,
                       elementIdProvider: SearchEverywhereMlItemIdProvider,
                       context: SearchEverywhereMLContextInfo,
                       cache: SearchEverywhereMlSearchState,
                       timeToFirstResult: Int,
                       elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    val experimentFromRegistry = Registry.intValue("search.everywhere.ml.experiment.group") >= 0
    val additional = listOf(
      CLOSE_POPUP_KEY to true,
      FORCE_EXPERIMENT_GROUP to experimentFromRegistry
    )
    reportElements(
      project, SESSION_FINISHED, seSessionId, searchIndex, experimentGroup, orderByMl,
      elementIdProvider, context, cache, timeToFirstResult, additional,
      EMPTY_ARRAY, emptyList(), elementsProvider
    )
  }

  fun onSearchRestarted(project: Project?, seSessionId: Int, searchIndex: Int,
                        experimentGroup: Int, orderByMl: Boolean,
                        elementIdProvider: SearchEverywhereMlItemIdProvider,
                        context: SearchEverywhereMLContextInfo,
                        cache: SearchEverywhereMlSearchState,
                        timeToFirstResult: Int,
                        elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    reportElements(
      project, SEARCH_RESTARTED, seSessionId, searchIndex, experimentGroup, orderByMl,
      elementIdProvider, context, cache, timeToFirstResult, emptyList(),
      EMPTY_ARRAY, emptyList(), elementsProvider
    )
  }

  private fun reportElements(project: Project?, eventId: String,
                             seSessionId: Int, searchIndex: Int,
                             experimentGroup: Int, orderByMl: Boolean,
                             elementIdProvider: SearchEverywhereMlItemIdProvider,
                             context: SearchEverywhereMLContextInfo,
                             state: SearchEverywhereMlSearchState,
                             timeToFirstResult: Int,
                             additional: List<Pair<String, Any>>,
                             selectedElements: IntArray,
                             selectedItems: List<Any>,
                             elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    val elements = elementsProvider.invoke()
    NonUrgentExecutor.getInstance().execute {
      val data = hashMapOf<String, Any>()
      data[PROJECT_OPENED_KEY] = project != null
      data[SESSION_ID_LOG_DATA_KEY] = seSessionId
      data[SEARCH_INDEX_DATA_KEY] = searchIndex
      data[TOTAL_NUMBER_OF_ITEMS_DATA_KEY] = elements.size
      data[SE_TAB_ID_KEY] = state.tabId
      data[EXPERIMENT_GROUP] = experimentGroup
      data[ORDER_BY_ML_GROUP] = orderByMl
      data[SEARCH_START_TIME_KEY] = state.searchStartTime

      if (timeToFirstResult > -1) {
        // Only report if some results came up in the search
        data[TIME_TO_FIRST_RESULT_DATA_KEY] = timeToFirstResult
      }

      data[TYPED_SYMBOL_KEYS] = state.keysTyped
      data[TYPED_BACKSPACES_DATA_KEY] = state.backspacesTyped
      data[REBUILD_REASON_KEY] = state.searchStartReason
      data.putAll(additional)
      data.putAll(context.features)

      val elementData = getElementsData(selectedElements, elements, elementIdProvider, selectedItems, project, state)
      data[IS_PROJECT_DISPOSED_KEY] = elementData == null
      if (elementData != null) {
        data.putAll(elementData)
      }
      loggerProvider.logger.logAsync(GROUP, eventId, data, false)
    }
  }

  private fun getElementsData(selectedElements: IntArray,
                              elements: List<SearchEverywhereFoundElementInfo>,
                              elementIdProvider: SearchEverywhereMlItemIdProvider,
                              selectedItems: List<Any>,
                              project: Project?,
                              state: SearchEverywhereMlSearchState): Map<String, Any>? {
    return hashMapOf<String, Any>().apply {
      putAll(getSelectedElementsData(selectedElements, elements, elementIdProvider, selectedItems))
      putAll(getCollectedElementsData(elements, project, elementIdProvider, state) ?: return null)
    }
  }

  private fun getSelectedElementsData(selectedElements: IntArray,
                                      elements: List<SearchEverywhereFoundElementInfo>,
                                      elementIdProvider: SearchEverywhereMlItemIdProvider,
                                      selectedItems: List<Any>): Map<String, Any> {
    val data = hashMapOf<String, Any>()
    if (selectedElements.isNotEmpty()) {
      data[SELECTED_INDEXES_DATA_KEY] = selectedElements.map { it.toString() }
      data[SELECTED_ELEMENTS_DATA_KEY] = selectedElements.map {
        if (it < elements.size) {
          val element = elements[it].element
          if (elementIdProvider.isElementSupported(element)) {
            return@map elementIdProvider.getId(element)
          }
        }
        return@map -1
      }
      data[SELECTED_ELEMENTS_CONSISTENT] = isSelectionConsistent(selectedElements, selectedItems, elements)
    }

    return data
  }

  /**
   * Gets features of the collected elements.
   * May return null if the project gets disposed.
   */
  private fun getCollectedElementsData(elements: List<SearchEverywhereFoundElementInfo>,
                                       project: Project?,
                                       elementIdProvider: SearchEverywhereMlItemIdProvider,
                                       state: SearchEverywhereMlSearchState): Map<String, Any>? {
    val data = hashMapOf<String, Any>()
    val actionManager = ActionManager.getInstance()
    data[COLLECTED_RESULTS_DATA_KEY] = elements.take(REPORTED_ITEMS_LIMIT).map {
      if (project != null && project.isDisposed) {
        return null
      }

      val result: HashMap<String, Any> = hashMapOf(
        CONTRIBUTOR_ID_KEY to it.contributor.searchProviderId
      )

      if (elementIdProvider.isElementSupported(it.element)) {
        // After changing tabs, it may happen that the code will run for an outdated list of results,
        // for example, results from All tab will be reported after switching to the Actions tab.
        // As this can lead to exceptions, we'll check if the element is supported, before collecting
        // features for it.
        addElementFeatures(elementIdProvider, it, state, result, actionManager)
      }
      result
    }

    return data
  }

  private fun isSelectionConsistent(selectedIndices: IntArray,
                                    selectedElements: List<Any>,
                                    elements: List<SearchEverywhereFoundElementInfo>): Boolean {
    if (selectedIndices.size != selectedElements.size) return false

    for (i in selectedIndices.indices) {
      val index = selectedIndices[i]
      if (index >= elements.size || selectedElements[i] != elements[index].element) {
        return false
      }
    }
    return true
  }

  private fun addElementFeatures(elementIdProvider: SearchEverywhereMlItemIdProvider,
                                 elementInfo: SearchEverywhereFoundElementInfo,
                                 state: SearchEverywhereMlSearchState,
                                 result: HashMap<String, Any>,
                                 actionManager: ActionManager) {
    val elementId = elementIdProvider.getId(elementInfo.element)
    val itemInfo = state.getElementFeatures(elementId, elementInfo.element, elementInfo.contributor, elementInfo.priority)
    if (itemInfo.features.isNotEmpty()) {
      result[FEATURES_DATA_KEY] = itemInfo.features
    }

    state.getMLWeightIfDefined(elementId)?.let { score ->
      result[ML_WEIGHT_KEY] = roundDouble(score)
    }

    result[ID_KEY] = itemInfo.id

    doWhenIsActionWrapper(elementInfo.element) {
      val action = it.action
      result[ACTION_ID_KEY] = actionManager.getId(action) ?: action.javaClass.name
    }
  }

  /**
   * Executes the operation when and only when [element] is [GotoActionModel.ActionWrapper].
   *
   * As the class is a public API, smart casting is not available and multiple casts are required,
   * the function simply takes care of that leaving the call site more concise.
   *
   * @param element: Element that may or may not be [GotoActionModel.ActionWrapper]
   * @param operation: Operation to be executed when the element is [GotoActionModel.ActionWrapper]
   */
  private fun doWhenIsActionWrapper(element: Any, operation: (GotoActionModel.ActionWrapper) -> Unit) {
    if ((element is GotoActionModel.MatchedValue) && (element.value is GotoActionModel.ActionWrapper)) {
      operation(element.value as GotoActionModel.ActionWrapper)
    }
  }

  companion object {
    private val GROUP = EventLogGroup("mlse.log", 17)
    private val EMPTY_ARRAY = IntArray(0)
    private const val REPORTED_ITEMS_LIMIT = 100

    // events
    private const val SESSION_FINISHED = "sessionFinished"
    private const val SEARCH_RESTARTED = "searchRestarted"

    private const val ORDER_BY_ML_GROUP = "orderByMl"
    private const val EXPERIMENT_GROUP = "experimentGroup"
    private const val FORCE_EXPERIMENT_GROUP = "isForceExperiment"

    private const val TIME_TO_FIRST_RESULT_DATA_KEY = "timeToFirstResult"

    // context fields
    private const val PROJECT_OPENED_KEY = "projectOpened"
    private const val IS_PROJECT_DISPOSED_KEY = "projectDisposed"
    private const val SE_TAB_ID_KEY = "seTabId"
    private const val CLOSE_POPUP_KEY = "closePopup"
    private const val SEARCH_START_TIME_KEY = "startTime"
    private const val REBUILD_REASON_KEY = "rebuildReason"
    private const val SESSION_ID_LOG_DATA_KEY = "sessionId"
    private const val SEARCH_INDEX_DATA_KEY = "searchIndex"
    private const val TYPED_SYMBOL_KEYS = "typedSymbolKeys"
    private const val TOTAL_NUMBER_OF_ITEMS_DATA_KEY = "totalItems"
    private const val TYPED_BACKSPACES_DATA_KEY = "typedBackspaces"
    private const val COLLECTED_RESULTS_DATA_KEY = "collectedItems"
    private const val SELECTED_INDEXES_DATA_KEY = "selectedIndexes"
    private const val SELECTED_ELEMENTS_DATA_KEY = "selectedIds"
    private const val SELECTED_ELEMENTS_CONSISTENT = "isConsistent"

    // item fields
    internal const val ID_KEY = "id"
    internal const val ACTION_ID_KEY = "actionId"
    internal const val FEATURES_DATA_KEY = "features"
    internal const val CONTRIBUTOR_ID_KEY = "contributorId"
    internal const val ML_WEIGHT_KEY = "mlWeight"

    private fun roundDouble(value: Double): Double {
      if (!value.isFinite()) return -1.0
      return round(value * 100000) / 100000
    }
  }
}