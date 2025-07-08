// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMixedListInfo
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereSessionPropertyProvider
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.log.MLSE_RECORDER_ID
import com.intellij.searchEverywhereMl.ranking.core.features.*
import com.intellij.searchEverywhereMl.ranking.core.id.SearchEverywhereMlItemIdProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

private const val REPORTED_ITEMS_LIMIT = 50

@ApiStatus.Internal
@IntellijInternalApi
object SearchEverywhereMLStatisticsCollector : CounterUsagesCollector() {
  private val contributorFeaturesProvider = SearchEverywhereContributorFeaturesProvider()

  override fun getGroup(): EventLogGroup = GROUP

  internal fun onItemSelected(project: Project?,
                              seSessionId: Int,
                              elementIdProvider: SearchEverywhereMlItemIdProvider,
                              cache: SearchEverywhereMlSearchState,
                              featureCache: SearchEverywhereMlFeaturesCache,
                              selectedIndices: IntArray,
                              selectedItems: List<Any>,
                              closePopup: Boolean,
                              timeToFirstResult: Int,
                              mixedListInfo: SearchEverywhereMixedListInfo,
                              elementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>,
                              sessionDurationMs: Int) {
    if (!isLoggingEnabled) return
    val elements = elementsProvider.invoke()
    val additionalEvents = buildList {
      addAll(getSelectedElementsEvents(selectedIndices, elements, elementIdProvider, selectedItems))
      addAll(getOnFinishEvents(closePopup, sessionDurationMs))
    }
    reportElements(
      project = project,
      eventId = SESSION_FINISHED,
      seSessionId = seSessionId,
      cache = cache,
      featureCache = featureCache,
      timeToFirstResult = timeToFirstResult,
      mixedListInfo = mixedListInfo,
      elements = elements,
      elementIdProvider = elementIdProvider,
      additionalEvents = additionalEvents
    )
  }

  internal fun onSearchFinished(project: Project?,
                                seSessionId: Int,
                                elementIdProvider: SearchEverywhereMlItemIdProvider,
                                cache: SearchEverywhereMlSearchState,
                                featureCache: SearchEverywhereMlFeaturesCache,
                                timeToFirstResult: Int,
                                mixedListInfo: SearchEverywhereMixedListInfo,
                                elementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>,
                                sessionDurationMs: Int) {
    if (!isLoggingEnabled) return
    val elements = elementsProvider.invoke()
    val additionalEvents = getOnFinishEvents(closePopup = true, sessionDurationMs)
    reportElements(
      project = project,
      eventId = SESSION_FINISHED,
      seSessionId = seSessionId,
      cache = cache,
      featureCache = featureCache,
      timeToFirstResult = timeToFirstResult,
      mixedListInfo = mixedListInfo,
      elements = elements,
      elementIdProvider = elementIdProvider,
      additionalEvents = additionalEvents
    )
  }

  internal fun onSearchRestarted(project: Project?, seSessionId: Int,
                                 elementIdProvider: SearchEverywhereMlItemIdProvider,
                                 context: SearchEverywhereMLContextInfo,
                                 cache: SearchEverywhereMlSearchState,
                                 featureCache: SearchEverywhereMlFeaturesCache,
                                 timeToFirstResult: Int,
                                 mixedListInfo: SearchEverywhereMixedListInfo,
                                 elementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>) {
    if (!isLoggingEnabled) return
    val elements = elementsProvider.invoke()
    val additionalEvents = buildList {
      if (cache.searchStartReason == SearchRestartReason.SEARCH_STARTED) {
        addAll(
          getSessionLevelEvents(project, context)
        )
      }
    }
    reportElements(
      project = project,
      eventId = SEARCH_RESTARTED,
      seSessionId = seSessionId,
      cache = cache,
      featureCache = featureCache,
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
                             featureCache: SearchEverywhereMlFeaturesCache,
                             timeToFirstResult: Int,
                             mixedListInfo: SearchEverywhereMixedListInfo,
                             elements: List<SearchEverywhereFoundElementInfoWithMl>,
                             elementIdProvider: SearchEverywhereMlItemIdProvider,
                             additionalEvents: List<EventPair<*>>) {
    eventId.log(project) {
      val tabId = cache.tab.tabId
      addAll(additionalEvents)

      addAll(
        getCommonTypeLevelEvents(seSessionId = seSessionId,
                                 tabId = tabId,
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

      addAll(SearchEverywhereSessionPropertyProvider.getAllProperties(tabId))
      addAll(getElementsEvents(project, featureCache, elements, mixedListInfo, elementIdProvider,
                               cache.sessionStartTime))
    }
  }

  private fun getOnFinishEvents(closePopup: Boolean, sessionDurationMs: Int): List<EventPair<*>> {
    val experimentFromRegistry = Registry.intValue("search.everywhere.ml.experiment.group") >= 0
    return buildList {
      add(CLOSE_POPUP_KEY.with(closePopup))
      add(FORCE_EXPERIMENT_GROUP.with(experimentFromRegistry))

      if (closePopup) {
        // The following fields will be reported only when the Search Everywhere session has finished
        add(SESSION_DURATION.with(sessionDurationMs))
      }
    }
  }

  private fun getElementsEvents(project: Project?,
                                featureCache: SearchEverywhereMlFeaturesCache,
                                elements: List<SearchEverywhereFoundElementInfoWithMl>,
                                mixedListInfo: SearchEverywhereMixedListInfo,
                                elementIdProvider: SearchEverywhereMlItemIdProvider,
                                sessionStartTime: Long): List<EventPair<*>> {
    val contributorFeaturesProvider = { it: SearchEverywhereFoundElementInfoWithMl ->
      buildList {
        addAll(contributorFeaturesProvider.getFeatures(it.contributor, mixedListInfo, sessionStartTime))
        addAll(contributorFeaturesProvider.getEssentialContributorFeatures(it.contributor))
      }
    }

    val updateEvents = featureCache.getUpdateEventsAndCache(project, elements.take(REPORTED_ITEMS_LIMIT),
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
      val isInternal = ApplicationManager.getApplication().isInternal
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
      add(EXPERIMENT_VERSION.with(SearchEverywhereMlExperiment.VERSION))
      add(IS_INTERNAL.with(isInternal))
    }
  }

  private fun getSessionLevelEvents(project: Project?,
                                    context: SearchEverywhereMLContextInfo) = buildList {
    add(PROJECT_OPENED_KEY.with(project != null))
    addAll(context.features)
  }

  private val isLoggingEnabled: Boolean
    get() {
      val application = ApplicationManager.getApplication()
      return application.isUnitTestMode || (application.isEAP && !Registry.`is`("search.everywhere.force.disable.logging.ml"))
    }


  private fun getSelectedElementsEvents(selectedElements: IntArray,
                                        elements: List<SearchEverywhereFoundElementInfoWithMl>,
                                        elementIdProvider: SearchEverywhereMlItemIdProvider,
                                        selectedItems: List<Any>): List<EventPair<*>> {
    if (selectedElements.isEmpty()) return emptyList()

    return buildList {
      add(SELECTED_INDEXES_DATA_KEY.with(selectedElements.toList()))
      add(SELECTED_ELEMENTS_DATA_KEY.with(mapSelectedIndexToElementId(selectedElements, elements, elementIdProvider)))
      add(SELECTED_ELEMENTS_CONSISTENT.with(isSelectionConsistent(selectedElements, selectedItems, elements)))
    }
  }

  /**
   * Maps every selected element, based on index, to its ID
   * If the number of selected elements does not match the number of elements, each element will have ID -1.
   */
  private fun mapSelectedIndexToElementId(selectedIndices: IntArray,
                                          elements: List<SearchEverywhereFoundElementInfoWithMl>,
                                          idProvider: SearchEverywhereMlItemIdProvider): List<Int> {
    return ReadAction.compute<List<Int>, Nothing> {
      selectedIndices.map { index ->
        if (index > elements.lastIndex) return@map -1

        val element = elements[index].element
        return@map idProvider.getId(element) ?: -1
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

  private val GROUP = EventLogGroup("mlse.log", 120, MLSE_RECORDER_ID)

  private val IS_INTERNAL = EventFields.Boolean("isInternal")
  private val ORDER_BY_ML_GROUP = EventFields.Boolean("orderByMl")
  private val EXPERIMENT_GROUP = EventFields.Int("experimentGroup")
  private val EXPERIMENT_VERSION = EventFields.Int("experimentVersion")
  private val FORCE_EXPERIMENT_GROUP = EventFields.Boolean("isForceExperiment")
  @VisibleForTesting
  internal val SESSION_DURATION = EventFields.Int("sessionDuration", "Duration of the Search Everywhere session in ms")
  private val TIME_TO_FIRST_RESULT_DATA_KEY = EventFields.Int("timeToFirstResult")

  // context fields
  private val PROJECT_OPENED_KEY = EventFields.Boolean("projectOpened")
  private val IS_PROJECT_DISPOSED_KEY = EventFields.Boolean("projectDisposed")
  private val SE_TAB_ID_KEY = EventFields.String("seTabId", SearchEverywhereTab.allTabs.map { it.tabId })
  private val CLOSE_POPUP_KEY = EventFields.Boolean("closePopup")
  private val SEARCH_START_TIME_KEY = EventFields.Long("startTime")
  val REBUILD_REASON_KEY = EventFields.Enum<SearchRestartReason>("rebuildReason")
  private val SESSION_ID_LOG_DATA_KEY = EventFields.Int("sessionId")
  private val SEARCH_INDEX_DATA_KEY = EventFields.Int("searchIndex")
  private val TYPED_SYMBOL_KEYS = EventFields.Int("typedSymbolKeys")
  private val TOTAL_NUMBER_OF_ITEMS_DATA_KEY = EventFields.Int("totalItems")
  private val TYPED_BACKSPACES_DATA_KEY = EventFields.Int("typedBackspaces")
  private val SELECTED_INDEXES_DATA_KEY = EventFields.IntList("selectedIndexes")

  @VisibleForTesting
  val SELECTED_ELEMENTS_DATA_KEY = EventFields.IntList("selectedIds")
  private val SELECTED_ELEMENTS_CONSISTENT = EventFields.Boolean("isConsistent")

  private val IS_MIXED_LIST = EventFields.Boolean("isMixedList")

  // item fields
  private val SEARCH_STATE_FEATURES_DATA_KEY =
    ObjectEventField("searchStateFeatures", *SearchEverywhereStateFeaturesProvider.getFeaturesDefinition().toTypedArray())

  @VisibleForTesting
  val ID_KEY = EventFields.Int("id")

  @Suppress("DEPRECATION")
  val ACTION_ID_KEY = ActionsEventLogGroup.ActioID("actionId")

  @VisibleForTesting
  val FEATURES_DATA_KEY = createFeaturesEventObject()
  val ML_WEIGHT_KEY = EventFields.Double("mlWeight")
  val PRIORITY_KEY = EventFields.Int("priority", "The final priority used for sorting elements")
  val ABSENT_FEATURES_KEY = EventFields.StringListValidatedByCustomRule("absentFeatures",
                                                                        SearchEverywhereMlElementFeatureValidationRule::class.java)
  val CONTRIBUTOR_DATA_KEY = ObjectEventField(
    "contributor", *SearchEverywhereContributorFeaturesProvider.getFeaturesDeclarations().toTypedArray()
  )

  val COLLECTED_RESULTS_DATA_KEY = ObjectListEventField(
    "collectedItems", ID_KEY, ACTION_ID_KEY, FEATURES_DATA_KEY, CONTRIBUTOR_DATA_KEY, ML_WEIGHT_KEY, PRIORITY_KEY,
    ABSENT_FEATURES_KEY
  )

  // events
  @VisibleForTesting
  val SESSION_FINISHED = registerEvent("sessionFinished", CLOSE_POPUP_KEY, FORCE_EXPERIMENT_GROUP, SESSION_DURATION)
  val SEARCH_RESTARTED = registerEvent("searchRestarted")

  private val CLASSES_WITHOUT_KEY_PROVIDERS_FIELD = ClassListEventField("unsupported_classes")
  internal val KEY_NOT_COMPUTED_EVENT = GROUP.registerEvent("key.not.computed",
                                                            SESSION_ID_LOG_DATA_KEY,
                                                            CLASSES_WITHOUT_KEY_PROVIDERS_FIELD)

  private fun collectNameFeaturesToFields(): Map<String, EventField<*>> {
    val nameFeatureToField = hashMapOf<String, EventField<*>>(
      *SearchEverywhereElementFeaturesProvider.run {
        listOf(NAME_LENGTH, ML_SCORE_KEY, SIMILARITY_SCORE, IS_SEMANTIC_ONLY, BUFFERED_TIMESTAMP)
      }.map { it.name to it }.toTypedArray()
    )
    nameFeatureToField.putAll(SearchEverywhereElementFeaturesProvider.prefixMatchingNameFeatureToField.values.map { it.name to it })
    nameFeatureToField.putAll(SearchEverywhereElementFeaturesProvider.wholeMatchingNameFeatureToField.values.map { it.name to it })
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
      TOTAL_NUMBER_OF_ITEMS_DATA_KEY,
      SE_TAB_ID_KEY,
      EXPERIMENT_GROUP,
      EXPERIMENT_VERSION,
      ORDER_BY_ML_GROUP,
      IS_INTERNAL,
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
    fields.addAll(SearchEverywhereSessionPropertyProvider.getAllDeclarations())
    fields.addAll(SearchEverywhereContextFeaturesProvider.getContextFields())
    fields.addAll(additional)
    return GROUP.registerVarargEvent(eventId, *fields.toTypedArray())
  }
}
