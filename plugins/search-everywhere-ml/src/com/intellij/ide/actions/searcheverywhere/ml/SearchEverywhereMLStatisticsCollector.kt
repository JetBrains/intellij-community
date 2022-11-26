// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMixedListInfo
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereMlSessionService.Companion.RECORDER_CODE
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereContextFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereContributorFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereContributorFeaturesProvider.Companion.SE_TABS
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereElementFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereStateFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.id.SearchEverywhereMlItemIdProvider
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.NonUrgentExecutor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import kotlin.math.round

@ApiStatus.Internal
@IntellijInternalApi
class SearchEverywhereMLStatisticsCollector : CounterUsagesCollector() {
  private val contributorFeaturesProvider = SearchEverywhereContributorFeaturesProvider()

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  internal fun onItemSelected(project: Project?, seSessionId: Int, searchIndex: Int,
                     shouldLogFeatures: Boolean, experimentGroup: Int,
                     orderByMl: Boolean,
                     elementIdProvider: SearchEverywhereMlItemIdProvider,
                     context: SearchEverywhereMLContextInfo,
                     cache: SearchEverywhereMlSearchState,
                     selectedIndices: IntArray,
                     selectedItems: List<Any>,
                     closePopup: Boolean,
                     timeToFirstResult: Int,
                     mixedListInfo: SearchEverywhereMixedListInfo,
                     elementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>) {
    val experimentFromRegistry = Registry.intValue("search.everywhere.ml.experiment.group") >= 0
    val data = arrayListOf<EventPair<*>>(
      CLOSE_POPUP_KEY.with(closePopup),
      FORCE_EXPERIMENT_GROUP.with(experimentFromRegistry)
    )
    reportElements(
      project, SESSION_FINISHED, seSessionId, searchIndex, shouldLogFeatures, experimentGroup,
      orderByMl, elementIdProvider, context, cache, timeToFirstResult, data,
      selectedIndices, selectedItems, mixedListInfo, elementsProvider,
    )
  }

  internal fun onSearchFinished(project: Project?, seSessionId: Int, searchIndex: Int,
                       shouldLogFeatures: Boolean, experimentGroup: Int,
                       orderByMl: Boolean,
                       elementIdProvider: SearchEverywhereMlItemIdProvider,
                       context: SearchEverywhereMLContextInfo,
                       cache: SearchEverywhereMlSearchState,
                       timeToFirstResult: Int,
                       mixedListInfo: SearchEverywhereMixedListInfo,
                       elementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>) {
    val experimentFromRegistry = Registry.intValue("search.everywhere.ml.experiment.group") >= 0
    val additional = listOf(
      CLOSE_POPUP_KEY.with(true),
      FORCE_EXPERIMENT_GROUP.with(experimentFromRegistry)
    )
    reportElements(
      project, SESSION_FINISHED, seSessionId, searchIndex, shouldLogFeatures, experimentGroup,
      orderByMl, elementIdProvider, context, cache, timeToFirstResult, additional,
      EMPTY_ARRAY, emptyList(), mixedListInfo, elementsProvider,
    )
  }

  internal fun onSearchRestarted(project: Project?, seSessionId: Int, searchIndex: Int,
                        shouldLogFeatures: Boolean,
                        elementIdProvider: SearchEverywhereMlItemIdProvider,
                        context: SearchEverywhereMLContextInfo,
                        cache: SearchEverywhereMlSearchState,
                        timeToFirstResult: Int,
                        mixedListInfo: SearchEverywhereMixedListInfo,
                        elementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>) {
    reportElements(
      project, SEARCH_RESTARTED, seSessionId, searchIndex, shouldLogFeatures, cache.experimentGroup,
      cache.orderByMl, elementIdProvider, context, cache, timeToFirstResult,
      emptyList(), EMPTY_ARRAY, emptyList(), mixedListInfo, elementsProvider
    )
  }

  private fun reportElements(project: Project?, eventId: VarargEventId,
                             seSessionId: Int, searchIndex: Int,
                             shouldLogFeatures: Boolean, experimentGroup: Int,
                             orderByMl: Boolean,
                             elementIdProvider: SearchEverywhereMlItemIdProvider,
                             context: SearchEverywhereMLContextInfo,
                             state: SearchEverywhereMlSearchState,
                             timeToFirstResult: Int,
                             additional: List<EventPair<*>>,
                             selectedElements: IntArray,
                             selectedItems: List<Any>,
                             mixedListInfo: SearchEverywhereMixedListInfo,
                             elementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>) {
    if (!isLoggingEnabled()) return

    val elements = elementsProvider.invoke()
    NonUrgentExecutor.getInstance().execute {
      val data = ArrayList<EventPair<*>>()
      data.add(PROJECT_OPENED_KEY.with(project != null))
      data.add(SESSION_ID_LOG_DATA_KEY.with(seSessionId))
      data.add(SEARCH_INDEX_DATA_KEY.with(searchIndex))
      data.add(LOG_FEATURES_DATA_KEY.with(shouldLogFeatures))
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
      data.add(IS_MIXED_LIST.with(mixedListInfo.isMixedList))
      data.addAll(additional)
      if (shouldLogFeatures) {
        data.addAll(context.features)
      }

      val elementData = getElementsData(selectedElements, shouldLogFeatures, elements, elementIdProvider, selectedItems, project)
      data.add(IS_PROJECT_DISPOSED_KEY.with(elementData == null))
      if (elementData != null) {
        data.addAll(elementData)
      }

      val contributors = elements.map { element -> element.contributor }.toHashSet()
      data.add(CONTRIBUTORS.with(contributors.map { c ->
        val contributorInfo = contributorFeaturesProvider.getFeatures(c, mixedListInfo)
        ObjectEventData(contributorInfo)
      }))
      eventId.log(data)
    }
  }

  private fun isLoggingEnabled() =
    ApplicationManager.getApplication().isEAP && !Registry.`is`("search.everywhere.force.disable.logging.ml")

  private fun getElementsData(selectedElements: IntArray,
                              shouldLogFeatures: Boolean,
                              elements: List<SearchEverywhereFoundElementInfoWithMl>,
                              elementIdProvider: SearchEverywhereMlItemIdProvider,
                              selectedItems: List<Any>,
                              project: Project?): List<EventPair<*>>? {
    return ArrayList<EventPair<*>>().apply {
      addAll(getSelectedElementsData(selectedElements, elements, elementIdProvider, selectedItems))
      addAll(getCollectedElementsData(project, shouldLogFeatures, elements, elementIdProvider) ?: return null)
    }
  }

  private fun getSelectedElementsData(selectedElements: IntArray,
                                      elements: List<SearchEverywhereFoundElementInfoWithMl>,
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
  private fun getCollectedElementsData(project: Project?,
                                       shouldLogFeatures: Boolean,
                                       elements: List<SearchEverywhereFoundElementInfoWithMl>,
                                       elementIdProvider: SearchEverywhereMlItemIdProvider): ArrayList<EventPair<*>>? {
    val data = ArrayList<EventPair<*>>()
    val actionManager = ActionManager.getInstance()
    val value = elements.take(REPORTED_ITEMS_LIMIT).map {
      if (project != null && project.isDisposed) {
        return null
      }

      val result = arrayListOf<EventPair<*>>(
        CONTRIBUTOR_ID_KEY.with(it.contributor.searchProviderId)
      )

      val elementId = elementIdProvider.getId(it.element)
      elementId?.let { id -> result.add(ID_KEY.with(id)) }
      if (shouldLogFeatures) {
        addElementFeatures(it, result, actionManager)
      }
      ObjectEventData(result)
    }
    data.add(COLLECTED_RESULTS_DATA_KEY.with(value))

    return data
  }

  private fun isSelectionConsistent(selectedIndices: IntArray,
                                    selectedElements: List<Any>,
                                    elements: List<SearchEverywhereFoundElementInfoWithMl>): Boolean {
    if (selectedIndices.size != selectedElements.size) return false

    for (i in selectedIndices.indices) {
      val index = selectedIndices[i]
      if (index >= elements.size || selectedElements[i] != elements[index].element) {
        return false
      }
    }
    return true
  }

  private fun addElementFeatures(elementInfo: SearchEverywhereFoundElementInfoWithMl,
                                 result: MutableList<EventPair<*>>,
                                 actionManager: ActionManager) {
    if (elementInfo.mlFeatures.isNotEmpty()) {
      result.add(FEATURES_DATA_KEY.with(ObjectEventData(elementInfo.mlFeatures)))
    }

    val mlWeight = elementInfo.mlWeight ?: -1.0
    if (mlWeight >= 0.0) {
      result.add(ML_WEIGHT_KEY.with(roundDouble(mlWeight)))
    }

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
    private val GROUP = EventLogGroup("mlse.log", 38, RECORDER_CODE)
    private val EMPTY_ARRAY = IntArray(0)
    private const val REPORTED_ITEMS_LIMIT = 50

    private val ORDER_BY_ML_GROUP = EventFields.Boolean("orderByMl")
    private val EXPERIMENT_GROUP = EventFields.Int("experimentGroup")
    private val FORCE_EXPERIMENT_GROUP = EventFields.Boolean("isForceExperiment")
    private val TIME_TO_FIRST_RESULT_DATA_KEY = EventFields.Int("timeToFirstResult")

    // context fields
    private val PROJECT_OPENED_KEY = EventFields.Boolean("projectOpened")
    private val IS_PROJECT_DISPOSED_KEY = EventFields.Boolean("projectDisposed")
    private val SE_TAB_ID_KEY = EventFields.String("seTabId", SE_TABS)
    private val CLOSE_POPUP_KEY = EventFields.Boolean("closePopup")
    private val SEARCH_START_TIME_KEY = EventFields.Long("startTime")
    private val REBUILD_REASON_KEY = EventFields.Enum<SearchRestartReason>("rebuildReason")
    private val SESSION_ID_LOG_DATA_KEY = EventFields.Int("sessionId")
    private val SEARCH_INDEX_DATA_KEY = EventFields.Int("searchIndex")
    private val LOG_FEATURES_DATA_KEY = EventFields.Boolean("logFeatures")
    private val TYPED_SYMBOL_KEYS = EventFields.Int("typedSymbolKeys")
    private val TOTAL_NUMBER_OF_ITEMS_DATA_KEY = EventFields.Int("totalItems")
    private val TYPED_BACKSPACES_DATA_KEY = EventFields.Int("typedBackspaces")
    private val SELECTED_INDEXES_DATA_KEY = EventFields.IntList("selectedIndexes")

    @VisibleForTesting
    val SELECTED_ELEMENTS_DATA_KEY = EventFields.IntList("selectedIds")
    private val SELECTED_ELEMENTS_CONSISTENT = EventFields.Boolean("isConsistent")

    private val IS_MIXED_LIST = EventFields.Boolean("isMixedList")

    // item fields
    internal val SEARCH_STATE_FEATURES_DATA_KEY =
      ObjectEventField("searchStateFeatures", *SearchEverywhereStateFeaturesProvider.getFeaturesDefinition().toTypedArray())

    internal val ID_KEY = EventFields.Int("id")
    internal val ACTION_ID_KEY = EventFields.StringValidatedByCustomRule("actionId", "action")
    internal val FEATURES_DATA_KEY = createFeaturesEventObject()
    internal val CONTRIBUTOR_ID_KEY = EventFields.String("contributorId", SE_TABS)
    internal val ML_WEIGHT_KEY = EventFields.Double("mlWeight")

    @VisibleForTesting
    val COLLECTED_RESULTS_DATA_KEY = ObjectListEventField(
      "collectedItems", ID_KEY, ACTION_ID_KEY, FEATURES_DATA_KEY, CONTRIBUTOR_ID_KEY, ML_WEIGHT_KEY
    )

    private val CONTRIBUTORS = ObjectListEventField("contributors",
                                                    *SearchEverywhereContributorFeaturesProvider.getFeaturesDeclarations().toTypedArray())

    // events
    @VisibleForTesting
    val SESSION_FINISHED = registerEvent("sessionFinished", CLOSE_POPUP_KEY, FORCE_EXPERIMENT_GROUP)
    private val SEARCH_RESTARTED = registerEvent("searchRestarted")

    private fun createFeaturesEventObject(): ObjectEventField {
      val features = arrayListOf<EventField<*>>(SearchEverywhereElementFeaturesProvider.NAME_LENGTH)
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
        LOG_FEATURES_DATA_KEY,
        TOTAL_NUMBER_OF_ITEMS_DATA_KEY,
        SE_TAB_ID_KEY,
        EXPERIMENT_GROUP,
        ORDER_BY_ML_GROUP,
        SEARCH_START_TIME_KEY,
        TIME_TO_FIRST_RESULT_DATA_KEY,
        TYPED_SYMBOL_KEYS,
        TYPED_BACKSPACES_DATA_KEY,
        REBUILD_REASON_KEY,
        IS_MIXED_LIST,
        IS_PROJECT_DISPOSED_KEY,
        SELECTED_INDEXES_DATA_KEY,
        SELECTED_ELEMENTS_DATA_KEY,
        SELECTED_ELEMENTS_CONSISTENT,
        SEARCH_STATE_FEATURES_DATA_KEY,
        CONTRIBUTORS,
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