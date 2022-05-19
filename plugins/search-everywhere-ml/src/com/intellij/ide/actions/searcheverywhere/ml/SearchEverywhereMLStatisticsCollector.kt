// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereMlSessionService.Companion.RECORDER_CODE
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereContextFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereElementFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereStateFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.id.SearchEverywhereMlItemIdProvider
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.NonUrgentExecutor
import kotlin.math.round

internal class SearchEverywhereMLStatisticsCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

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
    val data = arrayListOf<EventPair<*>>(
      CLOSE_POPUP_KEY.with(closePopup),
      FORCE_EXPERIMENT_GROUP.with(experimentFromRegistry)
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
      CLOSE_POPUP_KEY.with(true),
      FORCE_EXPERIMENT_GROUP.with(experimentFromRegistry)
    )
    reportElements(
      project, SESSION_FINISHED, seSessionId, searchIndex, experimentGroup, orderByMl,
      elementIdProvider, context, cache, timeToFirstResult, additional,
      EMPTY_ARRAY, emptyList(), elementsProvider
    )
  }

  fun onSearchRestarted(project: Project?, seSessionId: Int, searchIndex: Int,
                        elementIdProvider: SearchEverywhereMlItemIdProvider,
                        context: SearchEverywhereMLContextInfo,
                        cache: SearchEverywhereMlSearchState,
                        timeToFirstResult: Int,
                        elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    reportElements(
      project, SEARCH_RESTARTED, seSessionId, searchIndex, cache.experimentGroup, cache.orderByMl,
      elementIdProvider, context, cache, timeToFirstResult, emptyList(),
      EMPTY_ARRAY, emptyList(), elementsProvider
    )
  }

  private fun reportElements(project: Project?, eventId: VarargEventId,
                             seSessionId: Int, searchIndex: Int,
                             experimentGroup: Int, orderByMl: Boolean,
                             elementIdProvider: SearchEverywhereMlItemIdProvider,
                             context: SearchEverywhereMLContextInfo,
                             state: SearchEverywhereMlSearchState,
                             timeToFirstResult: Int,
                             additional: List<EventPair<*>>,
                             selectedElements: IntArray,
                             selectedItems: List<Any>,
                             elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    val elements = elementsProvider.invoke()
    NonUrgentExecutor.getInstance().execute {
      val data = ArrayList<EventPair<*>>()
      data.add(PROJECT_OPENED_KEY.with(project != null))
      data.add(SESSION_ID_LOG_DATA_KEY.with(seSessionId))
      data.add(SEARCH_INDEX_DATA_KEY.with(searchIndex))
      data.add(TOTAL_NUMBER_OF_ITEMS_DATA_KEY.with(elements.size))
      data.add(SE_TAB_ID_KEY.with(state.tabId))
      data.add(EXPERIMENT_GROUP.with(experimentGroup))
      data.add(ORDER_BY_ML_GROUP.with(orderByMl))
      data.add(SEARCH_START_TIME_KEY.with(state.searchStartTime))
      data.add(SEARCH_STATE_FEATURES_DATA_KEY.with(ObjectEventData(state.searchStateFeatures)))

      if (timeToFirstResult > -1) {
        // Only report if some results came up in the search
        data.add(TIME_TO_FIRST_RESULT_DATA_KEY.with(timeToFirstResult))
      }

      data.add(TYPED_SYMBOL_KEYS.with(state.keysTyped))
      data.add(TYPED_BACKSPACES_DATA_KEY.with(state.backspacesTyped))
      data.add(REBUILD_REASON_KEY.with(state.searchStartReason))
      data.addAll(additional)
      data.addAll(context.features)

      val elementData = getElementsData(selectedElements, elements, elementIdProvider, selectedItems, project, state)
      data.add(IS_PROJECT_DISPOSED_KEY.with(elementData == null))
      if (elementData != null) {
        data.addAll(elementData)
      }
      eventId.log(data)
    }
  }

  private fun getElementsData(selectedElements: IntArray,
                              elements: List<SearchEverywhereFoundElementInfo>,
                              elementIdProvider: SearchEverywhereMlItemIdProvider,
                              selectedItems: List<Any>,
                              project: Project?,
                              state: SearchEverywhereMlSearchState): List<EventPair<*>>? {
    return ArrayList<EventPair<*>>().apply {
      addAll(getSelectedElementsData(selectedElements, elements, elementIdProvider, selectedItems))
      addAll(getCollectedElementsData(elements, project, elementIdProvider, state) ?: return null)
    }
  }

  private fun getSelectedElementsData(selectedElements: IntArray,
                                      elements: List<SearchEverywhereFoundElementInfo>,
                                      elementIdProvider: SearchEverywhereMlItemIdProvider,
                                      selectedItems: List<Any>): List<EventPair<*>> {
    val data = ArrayList<EventPair<*>>()
    if (selectedElements.isNotEmpty()) {
      data.add(SELECTED_INDEXES_DATA_KEY.with(selectedElements.map { it }))
      data.add(SELECTED_ELEMENTS_DATA_KEY.with(selectedElements.map {
        if (it < elements.size) {
          val element = elements[it].element
          val elementId = elementIdProvider.getId(element)
          if (elementId != null) {
            return@map elementId
          }
        }
        return@map -1
      }))
      data.add(SELECTED_ELEMENTS_CONSISTENT.with(isSelectionConsistent(selectedElements, selectedItems, elements)))
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
                                       state: SearchEverywhereMlSearchState): ArrayList<EventPair<*>>? {
    val data = ArrayList<EventPair<*>>()
    val actionManager = ActionManager.getInstance()
    val value = elements.take(REPORTED_ITEMS_LIMIT).map {
      if (project != null && project.isDisposed) {
        return null
      }

      val result = arrayListOf<EventPair<*>>(
        CONTRIBUTOR_ID_KEY.with(it.contributor.searchProviderId)
      )

      addElementFeatures(elementIdProvider.getId(it.element), it, state, result, actionManager)
      ObjectEventData(result)
    }
    data.add(COLLECTED_RESULTS_DATA_KEY.with(value))

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

  private fun addElementFeatures(elementId: Int?,
                                 elementInfo: SearchEverywhereFoundElementInfo,
                                 state: SearchEverywhereMlSearchState,
                                 result: MutableList<EventPair<*>>,
                                 actionManager: ActionManager) {
    val itemInfo = state.getElementFeatures(elementId, elementInfo.element, elementInfo.contributor, elementInfo.priority)
    if (itemInfo.features.isNotEmpty()) {
      result.add(FEATURES_DATA_KEY.with(ObjectEventData(itemInfo.features)))
    }

    state.getMLWeightIfDefined(elementId)?.let { score ->
      result.add(ML_WEIGHT_KEY.with(roundDouble(score)))
    }

    itemInfo.id?.let { result.add(ID_KEY.with(it)) }

    doWhenIsActionWrapper(elementInfo.element) {
      val action = it.action
      result.add(ACTION_ID_KEY.with(actionManager.getId(action) ?: action.javaClass.name))
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
    private val GROUP = EventLogGroup("mlse.log", 24, RECORDER_CODE)
    private val EMPTY_ARRAY = IntArray(0)
    private const val REPORTED_ITEMS_LIMIT = 100

    private val ORDER_BY_ML_GROUP = EventFields.Boolean("orderByMl")
    private val EXPERIMENT_GROUP = EventFields.Int("experimentGroup")
    private val FORCE_EXPERIMENT_GROUP = EventFields.Boolean("isForceExperiment")
    private val TIME_TO_FIRST_RESULT_DATA_KEY = EventFields.Int("timeToFirstResult")

    private val SE_TABS = listOf(
      "SearchEverywhereContributor.All", "ClassSearchEverywhereContributor",
      "FileSearchEverywhereContributor", "RecentFilesSEContributor",
      "SymbolSearchEverywhereContributor", "ActionSearchEverywhereContributor",
      "RunConfigurationsSEContributor", "CommandsContributor",
      "TopHitSEContributor", "com.intellij.ide.actions.searcheverywhere.CalculatorSEContributor",
      "TmsSearchEverywhereContributor", "YAMLKeysSearchEverywhereContributor",
      "UrlSearchEverywhereContributor", "Vcs.Git", "AutocompletionContributor",
      "TextSearchContributor", "DbSETablesContributor", "third.party"
    )

    // context fields
    private val PROJECT_OPENED_KEY = EventFields.Boolean("projectOpened")
    private val IS_PROJECT_DISPOSED_KEY = EventFields.Boolean("projectDisposed")
    private val SE_TAB_ID_KEY = EventFields.String("seTabId", SE_TABS)
    private val CLOSE_POPUP_KEY = EventFields.Boolean("closePopup")
    private val SEARCH_START_TIME_KEY = EventFields.Long("startTime")
    private val REBUILD_REASON_KEY = EventFields.Enum<SearchRestartReason>("rebuildReason")
    private val SESSION_ID_LOG_DATA_KEY = EventFields.Int("sessionId")
    private val SEARCH_INDEX_DATA_KEY = EventFields.Int("searchIndex")
    private val TYPED_SYMBOL_KEYS = EventFields.Int("typedSymbolKeys")
    private val TOTAL_NUMBER_OF_ITEMS_DATA_KEY = EventFields.Int("totalItems")
    private val TYPED_BACKSPACES_DATA_KEY = EventFields.Int("typedBackspaces")
    private val SELECTED_INDEXES_DATA_KEY = EventFields.IntList("selectedIndexes")
    private val SELECTED_ELEMENTS_DATA_KEY = EventFields.IntList("selectedIds")
    private val SELECTED_ELEMENTS_CONSISTENT = EventFields.Boolean("isConsistent")

    // item fields
    internal val SEARCH_STATE_FEATURES_DATA_KEY =
      ObjectEventField("searchStateFeatures", *SearchEverywhereStateFeaturesProvider.getFeaturesDefinition().toTypedArray())

    internal val ID_KEY = EventFields.Int("id")
    internal val ACTION_ID_KEY = EventFields.StringValidatedByCustomRule("actionId", "action")
    internal val FEATURES_DATA_KEY = createFeaturesEventObject()
    internal val CONTRIBUTOR_ID_KEY = EventFields.String("contributorId", SE_TABS)
    internal val ML_WEIGHT_KEY = EventFields.Double("mlWeight")

    private val COLLECTED_RESULTS_DATA_KEY =
      ObjectListEventField("collectedItems", ID_KEY, ACTION_ID_KEY, FEATURES_DATA_KEY, CONTRIBUTOR_ID_KEY, ML_WEIGHT_KEY)

    // events
    private val SESSION_FINISHED = registerEvent("sessionFinished", CLOSE_POPUP_KEY, FORCE_EXPERIMENT_GROUP)
    private val SEARCH_RESTARTED = registerEvent("searchRestarted")

    private fun createFeaturesEventObject(): ObjectEventField {
      val features = arrayListOf<EventField<*>>()
      features.addAll(SearchEverywhereElementFeaturesProvider.nameFeatureToField.values)
      for (featureProvider in SearchEverywhereElementFeaturesProvider.getFeatureProviders()) {
        features.addAll(featureProvider.getFeaturesDeclarations())
      }
      return ObjectEventField("features", *features.toTypedArray())
    }

    private fun registerEvent(eventId: String, vararg additional: EventField<*>): VarargEventId {
      val fields = arrayListOf(
        PROJECT_OPENED_KEY,
        SESSION_ID_LOG_DATA_KEY,
        SEARCH_INDEX_DATA_KEY,
        TOTAL_NUMBER_OF_ITEMS_DATA_KEY,
        SE_TAB_ID_KEY,
        EXPERIMENT_GROUP,
        ORDER_BY_ML_GROUP,
        SEARCH_START_TIME_KEY,
        TIME_TO_FIRST_RESULT_DATA_KEY,
        TYPED_SYMBOL_KEYS,
        TYPED_BACKSPACES_DATA_KEY,
        REBUILD_REASON_KEY,
        IS_PROJECT_DISPOSED_KEY,
        SELECTED_INDEXES_DATA_KEY,
        SELECTED_ELEMENTS_DATA_KEY,
        SELECTED_ELEMENTS_CONSISTENT,
        SEARCH_STATE_FEATURES_DATA_KEY,
        COLLECTED_RESULTS_DATA_KEY
      )
      fields.addAll(SearchEverywhereContextFeaturesProvider.getContextFields())
      fields.addAll(additional)
      return GROUP.registerVarargEvent(eventId, *fields.toTypedArray())
    }

    private fun roundDouble(value: Double): Double {
      if (!value.isFinite()) return -1.0
      return round(value * 100000) / 100000
    }
  }
}