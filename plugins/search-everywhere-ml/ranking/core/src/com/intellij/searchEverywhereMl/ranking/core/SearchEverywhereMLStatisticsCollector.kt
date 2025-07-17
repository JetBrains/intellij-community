// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMixedListInfo
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.features.SearchEverywhereStateFeaturesProvider
import com.intellij.searchEverywhereMl.log.MLSE_RECORDER_ID
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereContextFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereContributorFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.core.id.SearchEverywhereMlItemIdProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

private const val REPORTED_ITEMS_LIMIT = 50

@ApiStatus.Internal
@IntellijInternalApi
object SearchEverywhereMLStatisticsCollector : CounterUsagesCollector() {
  private val contributorFeaturesProvider = SearchEverywhereContributorFeaturesProvider()

  override fun getGroup(): EventLogGroup = GROUP

  internal fun onItemSelected(
    project: Project?,
    seSessionId: Int,
    elementIdProvider: SearchEverywhereMlItemIdProvider,
    searchState: SearchEverywhereMlSearchState,
    selectedIndices: IntArray,
    selectedItems: List<Any>,
    closePopup: Boolean,
    timeToFirstResult: Int,
    mixedListInfo: SearchEverywhereMixedListInfo,
    searchResults: List<SearchEverywhereFoundElementInfoWithMl>,
    sessionDurationMs: Int,
  ) {
    if (!isLoggingEnabled) return
    val additionalEvents = buildList {
      addAll(getSelectedElementsEvents(selectedIndices, searchResults, elementIdProvider, selectedItems))
      addAll(getOnFinishEvents(closePopup, sessionDurationMs))
    }
    logEvent(
      project = project,
      eventId = SESSION_FINISHED,
      seSessionId = seSessionId,
      searchState = searchState,
      timeToFirstResult = timeToFirstResult,
      mixedListInfo = mixedListInfo,
      searchResults = searchResults,
      additionalEvents = additionalEvents
    )
  }

  internal fun onSearchFinished(
    project: Project?,
    seSessionId: Int,
    searchState: SearchEverywhereMlSearchState,
    timeToFirstResult: Int,
    mixedListInfo: SearchEverywhereMixedListInfo,
    searchResults: List<SearchEverywhereFoundElementInfoWithMl>,
    sessionDurationMs: Int,
  ) {
    if (!isLoggingEnabled) return
    val additionalEvents = getOnFinishEvents(closePopup = true, sessionDurationMs)
    logEvent(
      project = project,
      eventId = SESSION_FINISHED,
      seSessionId = seSessionId,
      searchState = searchState,
      timeToFirstResult = timeToFirstResult,
      mixedListInfo = mixedListInfo,
      searchResults = searchResults,
      additionalEvents = additionalEvents
    )
  }

  internal fun onSearchRestarted(
    project: Project?, seSessionId: Int,
    context: SearchEverywhereMLContextInfo,
    searchState: SearchEverywhereMlSearchState,
    timeToFirstResult: Int,
    mixedListInfo: SearchEverywhereMixedListInfo,
    searchResults: List<SearchEverywhereFoundElementInfoWithMl>,
  ) {
    if (!isLoggingEnabled) return
    val additionalEvents = buildList {
      if (searchState.searchRestartReason == SearchRestartReason.SEARCH_STARTED) {
        addAll(
          getSessionLevelEvents(project, context)
        )
      }
    }
    logEvent(
      project = project,
      eventId = SEARCH_RESTARTED,
      seSessionId = seSessionId,
      searchState = searchState,
      timeToFirstResult = timeToFirstResult,
      mixedListInfo = mixedListInfo,
      searchResults = searchResults,
      additionalEvents = additionalEvents
    )
  }

  private fun logEvent(
    project: Project?,
    eventId: VarargEventId,
    seSessionId: Int,
    searchState: SearchEverywhereMlSearchState,
    timeToFirstResult: Int,
    mixedListInfo: SearchEverywhereMixedListInfo,
    searchResults: List<SearchEverywhereFoundElementInfoWithMl>,
    additionalEvents: List<EventPair<*>>,
  ) {
    val contributors = searchResults.map { it.contributor }.distinct()

    eventId.log(project) {
      val tabId = searchState.tab.tabId
      addAll(additionalEvents)

      add(CONTRIBUTOR_FEATURES_LIST.with(
        contributors.map { contributor ->
          val contributorFeatures = buildList {
            addAll(contributorFeaturesProvider.getFeatures(contributor, mixedListInfo, searchState.sessionStartTime))
            addAll(contributorFeaturesProvider.getEssentialContributorFeatures(contributor))
          }

          ObjectEventData(contributorFeatures)
        }
      ))

      add(COLLECTED_RESULTS_DATA_KEY.with(
        searchResults.take(REPORTED_ITEMS_LIMIT)
          .map { element -> element.toObjectEventData() }
      ))

      addAll(
        getCommonTypeLevelEvents(seSessionId = seSessionId,
                                 tabId = tabId,
                                 elementsSize = searchResults.size,
                                 searchStateFeatures = searchState.searchStateFeatures,
                                 timeToFirstResult = timeToFirstResult,
                                 searchIndex = searchState.index,
                                 searchStartTime = searchState.stateStartTime,
                                 keysTyped = searchState.keysTyped,
                                 backspacesTyped = searchState.backspacesTyped,
                                 searchStartReason = searchState.searchRestartReason,
                                 isMixedList = mixedListInfo.isMixedList,
                                 orderByMl = searchState.orderByMl,
                                 experimentGroup = searchState.experimentGroup)
      )

      addAll(SearchEverywhereStateFeaturesProvider.getFeatures(searchState))

      add(IS_PROJECT_DISPOSED_KEY.with(project != null && project.isDisposed))
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

  private fun getCommonTypeLevelEvents(
    seSessionId: Int,
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
    experimentGroup: Int,
  ): List<EventPair<*>> {
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

  private fun getSessionLevelEvents(
    project: Project?,
    context: SearchEverywhereMLContextInfo,
  ) = buildList {
    add(PROJECT_OPENED_KEY.with(project != null))
    addAll(context.features)
  }

  private val isLoggingEnabled: Boolean
    get() {
      val application = ApplicationManager.getApplication()
      return application.isUnitTestMode || (application.isEAP && !Registry.`is`("search.everywhere.force.disable.logging.ml"))
    }


  private fun getSelectedElementsEvents(
    selectedElements: IntArray,
    elements: List<SearchEverywhereFoundElementInfoWithMl>,
    elementIdProvider: SearchEverywhereMlItemIdProvider,
    selectedItems: List<Any>,
  ): List<EventPair<*>> {
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
  private fun mapSelectedIndexToElementId(
    selectedIndices: IntArray,
    elements: List<SearchEverywhereFoundElementInfoWithMl>,
    idProvider: SearchEverywhereMlItemIdProvider,
  ): List<Int> {
    return ReadAction.compute<List<Int>, Nothing> {
      selectedIndices.map { index ->
        if (index > elements.lastIndex) return@map -1

        val element = elements[index].element
        return@map idProvider.getId(element) ?: -1
      }
    }
  }

  private fun isSelectionConsistent(
    selectedIndices: IntArray,
    selectedElements: List<Any>,
    elements: List<SearchEverywhereFoundElementInfoWithMl>,
  ): Boolean {
    if (selectedIndices.size != selectedElements.size) return false

    for (i in selectedIndices.indices) {
      val index = selectedIndices[i]
      if (index >= elements.size || selectedElements[i] != elements[index].element) {
        return false
      }
    }
    return true
  }

  private fun SearchEverywhereFoundElementInfoWithMl.toObjectEventData(): ObjectEventData {
    return ObjectEventData(
      buildList {
        this@toObjectEventData.elementId?.let { add(ID_KEY.with(it)) }
        add(ELEMENT_CONTRIBUTOR.with(contributor.searchProviderId))
        add(FEATURES_DATA_KEY.with(ObjectEventData(this@toObjectEventData.mlFeatures)))

        this@toObjectEventData.getActionIdOrNull()?.let { add(ACTION_ID_KEY.with(it)) }

        this@toObjectEventData.mlWeight?.let { add(ML_WEIGHT_KEY.with(it)) }

        add(PRIORITY_KEY.with(this@toObjectEventData.priority))
      }
    )
  }

  private fun SearchEverywhereFoundElementInfoWithMl.getActionIdOrNull(): String? {
    val actionManager = ActionManager.getInstance()
    val element = this.element

    return if ((element is GotoActionModel.MatchedValue) && (element.value is GotoActionModel.ActionWrapper)) {
      val action = (element.value as GotoActionModel.ActionWrapper).action
      actionManager.getId(action) ?: action.javaClass.name
    }
    else {
      null
    }
  }

  private val GROUP = EventLogGroup("mlse.log", 125, MLSE_RECORDER_ID)

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
  internal val REBUILD_REASON_KEY = EventFields.Enum<SearchRestartReason>("rebuildReason")
  private val SESSION_ID_LOG_DATA_KEY = EventFields.Int("sessionId")
  private val SEARCH_INDEX_DATA_KEY = EventFields.Int("searchIndex")
  private val TYPED_SYMBOL_KEYS = EventFields.Int("typedSymbolKeys")
  private val TOTAL_NUMBER_OF_ITEMS_DATA_KEY = EventFields.Int("totalItems")
  private val TYPED_BACKSPACES_DATA_KEY = EventFields.Int("typedBackspaces")
  private val SELECTED_INDEXES_DATA_KEY = EventFields.IntList("selectedIndexes")

  @VisibleForTesting
  val SELECTED_ELEMENTS_DATA_KEY: IntListEventField = EventFields.IntList("selectedIds")
  private val SELECTED_ELEMENTS_CONSISTENT = EventFields.Boolean("isConsistent")

  private val IS_MIXED_LIST = EventFields.Boolean("isMixedList")

  // item fields
  private val SEARCH_STATE_FEATURES_DATA_KEY =
    ObjectEventField("searchStateFeatures", *SearchEverywhereStateFeaturesProvider.getFields().toTypedArray())

  @VisibleForTesting
  val ID_KEY: IntEventField = EventFields.Int("id")

  @Suppress("DEPRECATION")
  internal val ACTION_ID_KEY = ActionsEventLogGroup.ActioID("actionId")

  @VisibleForTesting
  val FEATURES_DATA_KEY: ObjectEventField = createFeaturesEventObject()
  internal val ML_WEIGHT_KEY: DoubleEventField = EventFields.Double("mlWeight")
  internal val PRIORITY_KEY: IntEventField = EventFields.Int("priority", "The final priority used for sorting elements")
  internal val CONTRIBUTOR_FEATURES_LIST = ObjectListEventField(
    "contributors",
    *SearchEverywhereContributorFeaturesProvider.getFeaturesDeclarations().toTypedArray(),
    description = "Features of contributors that contributed to the search results",
  )

  /**
   * This field is used to record the name of the contributor that provided a specific element
   * within the context of a Search Everywhere action.
   */
  internal val ELEMENT_CONTRIBUTOR = EventFields.String("contributor",
                                                        SearchEverywhereContributorFeaturesProvider.SE_CONTRIBUTORS,
                                                        "Contributor name that provided the element")

  val COLLECTED_RESULTS_DATA_KEY: ObjectListEventField = ObjectListEventField(
    "collectedItems",
    ID_KEY, ELEMENT_CONTRIBUTOR, ACTION_ID_KEY,
    FEATURES_DATA_KEY, ML_WEIGHT_KEY, PRIORITY_KEY,
  )

  // events
  @VisibleForTesting
  val SESSION_FINISHED: VarargEventId = registerEvent("sessionFinished", CLOSE_POPUP_KEY, FORCE_EXPERIMENT_GROUP, SESSION_DURATION)
  internal val SEARCH_RESTARTED: VarargEventId = registerEvent("searchRestarted")

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
    val fields = buildList {
      addAll(listOf(
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
        COLLECTED_RESULTS_DATA_KEY,
        CONTRIBUTOR_FEATURES_LIST,
      ))

      addAll(SearchEverywhereStateFeaturesProvider.getFields())
      addAll(SearchEverywhereContextFeaturesProvider.getContextFields())
      addAll(additional)
    }

    return GROUP.registerVarargEvent(eventId, *fields.toTypedArray())
  }
}
