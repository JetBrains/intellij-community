// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMixedListInfo
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.SE_TABS
import com.intellij.searchEverywhereMl.log.MLSE_RECORDER_ID
import com.intellij.searchEverywhereMl.ranking.features.*
import com.intellij.searchEverywhereMl.ranking.id.SearchEverywhereMlItemIdProvider
import com.intellij.util.concurrency.NonUrgentExecutor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@ApiStatus.Internal
@IntellijInternalApi
class SearchEverywhereMLStatisticsCollector : CounterUsagesCollector() {
  private val contributorFeaturesProvider = SearchEverywhereContributorFeaturesProvider()
  private val featuresCache = SearchEverywhereMlFeaturesCache()

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  internal fun onItemSelected(project: Project?,
                              seSessionId: Int,
                              shouldLogFeatures: Boolean,
                              elementIdProvider: SearchEverywhereMlItemIdProvider,
                              cache: SearchEverywhereMlSearchState,
                              selectedIndices: IntArray,
                              selectedItems: List<Any>,
                              closePopup: Boolean,
                              timeToFirstResult: Int,
                              mixedListInfo: SearchEverywhereMixedListInfo,
                              elementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>) {
    if (!isLoggingEnabled()) return
    val elements = elementsProvider.invoke()
    val additionalEvents = buildList {
      addAll(getSelectedElementsEvents(selectedIndices, elements, elementIdProvider, selectedItems))
      addAll(getOnFinishEvents(closePopup))
    }
    reportElements(
      project = project,
      eventId = SESSION_FINISHED,
      seSessionId = seSessionId,
      cache = cache,
      shouldLogFeatures = shouldLogFeatures,
      timeToFirstResult = timeToFirstResult,
      mixedListInfo = mixedListInfo,
      elements = elements,
      elementIdProvider = elementIdProvider,
      additionalEvents = additionalEvents
    )
  }

  internal fun onSearchFinished(project: Project?,
                                seSessionId: Int,
                                shouldLogFeatures: Boolean,
                                elementIdProvider: SearchEverywhereMlItemIdProvider,
                                cache: SearchEverywhereMlSearchState,
                                timeToFirstResult: Int,
                                mixedListInfo: SearchEverywhereMixedListInfo,
                                elementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>) {
    if (!isLoggingEnabled()) return
    val elements = elementsProvider.invoke()
    val additionalEvents = getOnFinishEvents(closePopup = true)
    reportElements(
      project = project,
      eventId = SESSION_FINISHED,
      seSessionId = seSessionId,
      cache = cache,
      shouldLogFeatures = shouldLogFeatures,
      timeToFirstResult = timeToFirstResult,
      mixedListInfo = mixedListInfo,
      elements = elements,
      elementIdProvider = elementIdProvider,
      additionalEvents = additionalEvents
    )
  }

  internal fun onSearchRestarted(project: Project?, seSessionId: Int,
                                 shouldLogFeatures: Boolean,
                                 elementIdProvider: SearchEverywhereMlItemIdProvider,
                                 context: SearchEverywhereMLContextInfo,
                                 cache: SearchEverywhereMlSearchState,
                                 timeToFirstResult: Int,
                                 mixedListInfo: SearchEverywhereMixedListInfo,
                                 elementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>) {
    if (!isLoggingEnabled()) return
    val elements = elementsProvider.invoke()
    val additionalEvents = buildList {
      if (cache.searchStartReason == SearchRestartReason.SEARCH_STARTED) {
        addAll(
          getSessionLevelEvents(project, shouldLogFeatures, context)
        )
      }
    }
    reportElements(
      project = project,
      eventId = SEARCH_RESTARTED,
      seSessionId = seSessionId,
      cache = cache,
      shouldLogFeatures = shouldLogFeatures,
      timeToFirstResult = timeToFirstResult,
      mixedListInfo = mixedListInfo,
      elements = elements,
      elementIdProvider = elementIdProvider,
      additionalEvents = additionalEvents
    )
  }

  private fun reportElements(project: Project?,
                             eventId: VarargEventId,
                             seSessionId: Int,
                             cache: SearchEverywhereMlSearchState,
                             shouldLogFeatures: Boolean,
                             timeToFirstResult: Int,
                             mixedListInfo: SearchEverywhereMixedListInfo,
                             elements: List<SearchEverywhereFoundElementInfoWithMl>,
                             elementIdProvider: SearchEverywhereMlItemIdProvider,
                             additionalEvents: List<EventPair<*>>) {
    NonUrgentExecutor.getInstance().execute {
      val eventData = mutableListOf<EventPair<*>>()
      eventData.addAll(additionalEvents)

      eventData.addAll(
        getCommonTypeLevelEvents(seSessionId = seSessionId,
                                 tabId = cache.tabId,
                                 elementsSize = elements.size,
                                 searchStateFeatures = cache.searchStateFeatures,
                                 timeToFirstResult = timeToFirstResult,
                                 searchIndex = cache.searchIndex,
                                 searchStartTime = cache.searchStartTime,
                                 keysTyped = cache.keysTyped,
                                 backspacesTyped = cache.backspacesTyped,
                                 searchStartReason = cache.searchStartReason,
                                 isMixedList = mixedListInfo.isMixedList,
                                 orderByMl = cache.orderByMl,
                                 experimentGroup = cache.experimentGroup)
      )

      eventData.addAll(getElementsEvents(project, shouldLogFeatures, elements, mixedListInfo, elementIdProvider))

      eventId.log(eventData)
    }
  }

  private fun getOnFinishEvents(closePopup: Boolean): List<EventPair<Boolean>> {
    val experimentFromRegistry = Registry.intValue("search.everywhere.ml.experiment.group") >= 0
    return listOf(
      CLOSE_POPUP_KEY.with(closePopup),
      FORCE_EXPERIMENT_GROUP.with(experimentFromRegistry)
    )
  }

  private fun getElementsEvents(project: Project?, shouldLogFeatures: Boolean,
                                elements: List<SearchEverywhereFoundElementInfoWithMl>,
                                mixedListInfo: SearchEverywhereMixedListInfo,
                                elementIdProvider: SearchEverywhereMlItemIdProvider): List<EventPair<*>> {
    val contributorFeaturesProvider = { it: SearchEverywhereFoundElementInfoWithMl ->
      buildList {
        if (shouldLogFeatures) {
          addAll(contributorFeaturesProvider.getFeatures(it.contributor, mixedListInfo))
        }
        else {
          add(contributorFeaturesProvider.getContributorIdFeature(it.contributor))
        }
      }
    }

    val updateEvents = featuresCache.getUpdateEventsAndCache(project, shouldLogFeatures, elements.take(REPORTED_ITEMS_LIMIT),
                                                             contributorFeaturesProvider, elementIdProvider)
    val events = listOf(
      IS_PROJECT_DISPOSED_KEY.with(updateEvents == null)
    )

    if (updateEvents == null) return events

    return events + listOf(COLLECTED_RESULTS_DATA_KEY.with(updateEvents))
  }

  private fun getCommonTypeLevelEvents(seSessionId: Int,
                                       tabId: String,
                                       elementsSize: Int,
                                       searchStateFeatures: List<EventPair<*>>,
                                       timeToFirstResult: Int,
                                       searchIndex: Int,
                                       searchStartTime: Long,
                                       keysTyped: Int,
                                       backspacesTyped: Int,
                                       searchStartReason: SearchRestartReason,
                                       isMixedList: Boolean,
                                       orderByMl: Boolean,
                                       experimentGroup: Int): List<EventPair<*>> {
    return buildList {
      add(SE_TAB_ID_KEY.with(tabId))
      add(SESSION_ID_LOG_DATA_KEY.with(seSessionId))
      add(SEARCH_INDEX_DATA_KEY.with(searchIndex))
      add(SEARCH_START_TIME_KEY.with(searchStartTime))
      add(TOTAL_NUMBER_OF_ITEMS_DATA_KEY.with(elementsSize))
      add(SEARCH_STATE_FEATURES_DATA_KEY.with(ObjectEventData(searchStateFeatures)))
      if (timeToFirstResult > -1) {
        // Only report if some results came up in the search
        add(TIME_TO_FIRST_RESULT_DATA_KEY.with(timeToFirstResult))
      }
      add(TYPED_SYMBOL_KEYS.with(keysTyped))
      add(TYPED_BACKSPACES_DATA_KEY.with(backspacesTyped))
      add(REBUILD_REASON_KEY.with(searchStartReason))
      add(IS_MIXED_LIST.with(isMixedList))
      add(ORDER_BY_ML_GROUP.with(orderByMl))
      add(EXPERIMENT_GROUP.with(experimentGroup))
    }
  }

  private fun getSessionLevelEvents(
    project: Project?,
    shouldLogFeatures: Boolean,
    context: SearchEverywhereMLContextInfo
  ): List<EventPair<*>> {
    val sessionEvents = listOf(
      PROJECT_OPENED_KEY.with(project != null),
      LOG_FEATURES_DATA_KEY.with(shouldLogFeatures),
    )
    val contextEvents = if (shouldLogFeatures) context.features else emptyList()
    return sessionEvents + contextEvents
  }

  private fun isLoggingEnabled() =
    ApplicationManager.getApplication().isEAP && !Registry.`is`("search.everywhere.force.disable.logging.ml")


  private fun getSelectedElementsEvents(selectedElements: IntArray,
                                        elements: List<SearchEverywhereFoundElementInfoWithMl>,
                                        elementIdProvider: SearchEverywhereMlItemIdProvider,
                                        selectedItems: List<Any>): List<EventPair<*>> {
    return buildList {
      if (selectedElements.isNotEmpty()) {
        add(SELECTED_INDEXES_DATA_KEY.with(selectedElements.map { it }))
        add(SELECTED_ELEMENTS_DATA_KEY.with(selectedElements.map {
          if (it < elements.size) {
            val element = elements[it].element
            val elementId = elementIdProvider.getId(element)
            if (elementId != null) {
              return@map elementId
            }
          }
          return@map -1
        }))
        add(SELECTED_ELEMENTS_CONSISTENT.with(isSelectionConsistent(selectedElements, selectedItems, elements)))
      }
    }
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

  companion object {
    private val GROUP = EventLogGroup("mlse.log", 68, MLSE_RECORDER_ID)
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

    @VisibleForTesting
    val ID_KEY = EventFields.Int("id")
    internal val ACTION_ID_KEY = EventFields.StringValidatedByCustomRule("actionId", "action")

    @VisibleForTesting
    val FEATURES_DATA_KEY = createFeaturesEventObject()
    internal val ML_WEIGHT_KEY = EventFields.Double("mlWeight")
    internal val ABSENT_FEATURES_KEY = EventFields.StringListValidatedByCustomRule("absentFeatures",
                                                                                   SearchEverywhereMlElementFeatureValidationRule::class.java)
    internal val CONTRIBUTOR_DATA_KEY = ObjectEventField(
      "contributor", *SearchEverywhereContributorFeaturesProvider.getFeaturesDeclarations().toTypedArray()
    )

    val COLLECTED_RESULTS_DATA_KEY = ObjectListEventField(
      "collectedItems", ID_KEY, ACTION_ID_KEY, FEATURES_DATA_KEY, CONTRIBUTOR_DATA_KEY, ML_WEIGHT_KEY, ABSENT_FEATURES_KEY
    )


    // events
    @VisibleForTesting
    val SESSION_FINISHED = registerEvent("sessionFinished", CLOSE_POPUP_KEY, FORCE_EXPERIMENT_GROUP)
    private val SEARCH_RESTARTED = registerEvent("searchRestarted")

    private fun collectNameFeaturesToFields(): Map<String, EventField<*>> {
      val nameFeatureToField = hashMapOf<String, EventField<*>>(
        SearchEverywhereElementFeaturesProvider.NAME_LENGTH.name to SearchEverywhereElementFeaturesProvider.NAME_LENGTH,
        SearchEverywhereElementFeaturesProvider.ML_SCORE_KEY.name to SearchEverywhereElementFeaturesProvider.ML_SCORE_KEY
        )
      nameFeatureToField.putAll(SearchEverywhereElementFeaturesProvider.nameFeatureToField.values.map { it.name to it })
      for (featureProvider in SearchEverywhereElementFeaturesProvider.getFeatureProviders()) {
        nameFeatureToField.putAll(featureProvider.getFeaturesDeclarations().map {
          it.name to it
        })
      }
      return nameFeatureToField
    }

    internal fun findElementFeatureByName(name: String): EventField<*>? {
      return collectNameFeaturesToFields()[name]
    }

    private fun createFeaturesEventObject(): ObjectEventField {
      val nameFeatureToField = collectNameFeaturesToFields()
      return ObjectEventField("features", *nameFeatureToField.values.toTypedArray())
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
        COLLECTED_RESULTS_DATA_KEY
      )
      fields.addAll(SearchEverywhereContextFeaturesProvider.getContextFields())
      fields.addAll(additional)
      return GROUP.registerVarargEvent(eventId, *fields.toTypedArray())
    }
  }
}