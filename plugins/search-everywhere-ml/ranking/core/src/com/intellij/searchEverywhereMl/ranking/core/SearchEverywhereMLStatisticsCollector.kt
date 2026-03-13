// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereEssentialContributorMarker
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector.ALLOWED_CONTRIBUTOR_ID_LIST
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.ClassListEventField
import com.intellij.internal.statistic.eventLog.events.DoubleEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.eventLog.events.IntListEventField
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.ObjectListEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.MLSE_RECORDER_ID
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchResultAdapter
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchStateChangeReason
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereContextFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereContributorFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereStateFeaturesProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

private const val REPORTED_ITEMS_LIMIT = 50

@ApiStatus.Internal
@IntellijInternalApi
object SearchEverywhereMLStatisticsCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  internal fun onSessionStarted(project: Project?,
                                sessionId: Int,
                                tab: SearchEverywhereTab,
                                isNewSearchEverywhere: Boolean,
                                sessionStartTime: Long,
                                contextFeatures: List<EventPair<*>>,
                                isMixedList: Boolean) {
    if (!isLoggingEnabled) return

    SESSION_STARTED.log(project) {
      if (project != null) {
        add(IS_PROJECT_OPEN.with(project.isOpen))
        add(IS_PROJECT_DISPOSED_KEY.with(project.isDisposed))
      }

      add(SESSION_ID.with(sessionId))
      add(SE_TAB_ID_KEY.with(tab.tabId))
      add(IS_NEW_SEARCH_EVERYWHERE.with(isNewSearchEverywhere))
      add(EXPERIMENT_GROUP.with(SearchEverywhereMlExperiment.experimentGroup))
      add(EXPERIMENT_VERSION.with(SearchEverywhereMlExperiment.VERSION))
      add(FORCE_EXPERIMENT_GROUP.with(SearchEverywhereMlExperiment.isForcedExperimentGroupByRegistry))
      add(IS_INTERNAL.with(ApplicationManager.getApplication().isInternal))
      add(SEARCH_START_TIME_KEY.with(sessionStartTime))
      add(IS_MIXED_LIST.with(isMixedList))

      addAll(contextFeatures)
    }
  }

  internal fun onSearchRestarted(
    project: Project?,
    searchSession: SearchEverywhereMLSearchSession,
    searchState: SearchEverywhereMLSearchSession.SearchState,
    searchResults: List<SearchResultAdapter.Processed>,
    timeToFirstResult: Int,
  ) {
    if (!isLoggingEnabled) return

    // If ML essential contributor marker is available, we will get the list of contributors from it,
    // based on the predictions it has made.
    // Otherwise, we will take it from the list of results
    val contributors = SearchEverywhereEssentialContributorMarker.getInstanceOrNull()
                         ?.let { it as SearchEverywhereEssentialContributorMlMarker }
                         ?.getCachedPredictionsForState(searchState)
                         ?.keys
                       ?: searchResults.map { it.provider }.distinct()


    val contributorFeatures = contributors
      .map { contributor ->
        val contributorFeatures = searchState.getContributorFeatures(contributor)
        val essentialnessFeatures = SearchEverywhereContributorFeaturesProvider.getEssentialContributorFeatures(searchState, contributor)

        val allFeatures = contributorFeatures + essentialnessFeatures

        ObjectEventData(allFeatures)
      }

    val collectedResults = searchResults.take(REPORTED_ITEMS_LIMIT)
      .map { result -> result.toObjectEventData() }

    STATE_CHANGED.log(project) {
      add(SESSION_ID.with(searchSession.sessionId))
      add(SEARCH_INDEX_DATA_KEY.with(searchState.index))
      add(ORDER_BY_ML_GROUP.with(searchState.orderByMl))
      add(TOTAL_NUMBER_OF_ITEMS_DATA_KEY.with(searchResults.size))
      add(SE_TAB_ID_KEY.with(searchState.tab.tabId))
      add(TIME_TO_FIRST_RESULT_DATA_KEY.with(timeToFirstResult))
      add(REBUILD_REASON_KEY.with(searchState.searchStateChangeReason))
      add(WAS_INTERRUPTED.with(searchState.wasInterrupted))
      add(SEARCH_STATE_FEATURES_DATA_KEY.with(ObjectEventData(searchState.searchStateFeatures)))
      add(COLLECTED_RESULTS_DATA_KEY.with(collectedResults))
      add(CONTRIBUTOR_FEATURES_LIST.with(contributorFeatures))
    }
  }

  internal fun onItemSelected(project: Project?, sessionId: Int, searchIndex: Int, selectedResult: Pair<Int, SearchResultAdapter.Processed>) {
    if (!isLoggingEnabled) return

    ITEM_SELECTED.log(project) {
      add(SESSION_ID.with(sessionId))
      add(SEARCH_INDEX_DATA_KEY.with(searchIndex))
      add(SELECTED_INDEX.with(selectedResult.first))
      selectedResult.second.sessionWideId?.let { add(SELECTED_RESULT_ID.with(it.value)) }
    }
  }

  internal fun onSessionFinished(project: Project?, sessionId: Int, tab: SearchEverywhereTab, sessionDurationMs: Int) {
    if (!isLoggingEnabled) return

    SESSION_FINISHED.log(project) {
      add(SESSION_ID.with(sessionId))
      add(SESSION_DURATION.with(sessionDurationMs))
      add(SE_TAB_ID_KEY.with(tab.tabId))
    }
  }

  private val isLoggingEnabled: Boolean
    get() {
      val application = ApplicationManager.getApplication()
      return application.isUnitTestMode || (application.isEAP && !Registry.`is`("search.everywhere.force.disable.logging.ml"))
    }


  private fun SearchResultAdapter.Processed.toObjectEventData(): ObjectEventData {
    val searchResultData = buildList {
      if (this@toObjectEventData.sessionWideId != null) {
        add(ID_KEY.with(this@toObjectEventData.sessionWideId.value))
      }

      add(ELEMENT_CONTRIBUTOR.with(this@toObjectEventData.provider.id))

      if (this@toObjectEventData.mlFeatures != null) {
        add(FEATURES_DATA_KEY.with(ObjectEventData(this@toObjectEventData.mlFeatures)))
      }

      if (this@toObjectEventData.mlProbability != null) {
        add(ML_WEIGHT_KEY.with(this@toObjectEventData.mlProbability.value))
      }

      this@toObjectEventData.getActionIdOrNull()?.let { actionId ->
        add(ACTION_ID_KEY.with(actionId))
      }

      add(PRIORITY_KEY.with(this@toObjectEventData.finalPriority))
    }

    return ObjectEventData(searchResultData)
  }

  private fun SearchResultAdapter.Processed.getActionIdOrNull(): String? {
    val actionManager = ActionManager.getInstance()
    val element = this.rawItem

    return if ((element is GotoActionModel.MatchedValue) && (element.value is GotoActionModel.ActionWrapper)) {
      val action = (element.value as GotoActionModel.ActionWrapper).action
      actionManager.getId(action) ?: action.javaClass.name
    }
    else {
      null
    }
  }

  internal val GROUP = EventLogGroup("mlse.log", 134, MLSE_RECORDER_ID,
                                     "ML in Search Everywhere Log Group")

  // region Fields
  internal val IS_INTERNAL = EventFields.Boolean("is_internal")
  private val ORDER_BY_ML_GROUP = EventFields.Boolean("order_by_ml")
  internal val EXPERIMENT_GROUP = EventFields.Int("experiment_group")
  internal val EXPERIMENT_VERSION = EventFields.Int("experiment_version")
  private val FORCE_EXPERIMENT_GROUP = EventFields.Boolean("is_force_experiment")
  private val IS_NEW_SEARCH_EVERYWHERE = EventFields.Boolean("is_new_search_everywhere")

  @VisibleForTesting
  internal val SESSION_DURATION = EventFields.Int("session_duration", "Duration of the Search Everywhere session in ms")
  private val TIME_TO_FIRST_RESULT_DATA_KEY = EventFields.Int("time_to_first_result")

  // context allFields
  private val IS_PROJECT_OPEN = EventFields.Boolean("is_project_open")
  private val IS_PROJECT_DISPOSED_KEY = EventFields.Boolean("project_disposed")
  private val IS_MIXED_LIST = EventFields.Boolean("is_mixed_list")
  internal val SE_TAB_ID_KEY = EventFields.String("se_tab_id", ALLOWED_CONTRIBUTOR_ID_LIST)
  internal val SEARCH_START_TIME_KEY = EventFields.Long("start_time")
  internal val REBUILD_REASON_KEY = EventFields.Enum<SearchStateChangeReason>("rebuild_reason")
  internal val SESSION_ID = EventFields.Int("session_id")
  internal val SEARCH_INDEX_DATA_KEY = EventFields.Int("search_index")

  private val TOTAL_NUMBER_OF_ITEMS_DATA_KEY = EventFields.Int("total_items")
  private val WAS_INTERRUPTED = EventFields.Boolean("was_interrupted",
                                                    "Whether the search state was interrupted before completing (e.g., due to a new query)")

  internal val SELECTED_INDEX = EventFields.Int("selected_index", "Selected index (0-based) of the item")
  internal val SELECTED_RESULT_ID = EventFields.Int("result_id", "Session-wide ID of the selected result")

  @VisibleForTesting
  val SELECTED_ELEMENTS_DATA_KEY: IntListEventField = EventFields.IntList("selected_ids")

  // item allFields
  private val SEARCH_STATE_FEATURES_DATA_KEY =
    ObjectEventField("search_state_features", *SearchEverywhereStateFeaturesProvider.allFields.toTypedArray())

  @VisibleForTesting
  val ID_KEY: IntEventField = EventFields.Int("id")

  internal val ACTION_ID_KEY = ActionsEventLogGroup.ACTION_ID

  @VisibleForTesting
  val FEATURES_DATA_KEY: ObjectEventField = ObjectEventField(
    "features",
    *buildList {
      addAll(SearchEverywhereElementFeaturesProvider.getDefaultFields())

      SearchEverywhereElementFeaturesProvider.getFeatureProviders()
        .forEach { featuresProvider ->
          addAll(featuresProvider.getFeaturesDeclarations())
        }
    }.toTypedArray()
  )
  internal val ML_WEIGHT_KEY: DoubleEventField = EventFields.Double("ml_weight")
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
                                                        ALLOWED_CONTRIBUTOR_ID_LIST,
                                                        "Contributor name that provided the element")

  val COLLECTED_RESULTS_DATA_KEY: ObjectListEventField = ObjectListEventField(
    "collected_items",
    ID_KEY, ELEMENT_CONTRIBUTOR, ACTION_ID_KEY,
    FEATURES_DATA_KEY, ML_WEIGHT_KEY, PRIORITY_KEY,
  )

  private val CLASSES_WITHOUT_KEY_PROVIDERS_FIELD = ClassListEventField("unsupported_classes")
  // endregion
  // region Events
  internal val SESSION_STARTED: VarargEventId = GROUP.registerVarargEvent("session.started",
                                                                          "An event denoting a start of Search Everywhere session",
                                                                          SESSION_ID, IS_PROJECT_OPEN,
                                                                          SE_TAB_ID_KEY, EXPERIMENT_GROUP, EXPERIMENT_VERSION,
                                                                          IS_INTERNAL, SEARCH_START_TIME_KEY,
                                                                          IS_PROJECT_DISPOSED_KEY,
                                                                          FORCE_EXPERIMENT_GROUP,
                                                                          IS_MIXED_LIST,
                                                                          IS_NEW_SEARCH_EVERYWHERE,
                                                                          *SearchEverywhereContextFeaturesProvider.getContextFields().toTypedArray())

  internal val STATE_CHANGED: VarargEventId = GROUP.registerVarargEvent("state.changed",
                                                                        "An event denoting change of the search state",
                                                                        SESSION_ID, SEARCH_INDEX_DATA_KEY,
                                                                        ORDER_BY_ML_GROUP,
                                                                        TOTAL_NUMBER_OF_ITEMS_DATA_KEY, SE_TAB_ID_KEY,
                                                                        TIME_TO_FIRST_RESULT_DATA_KEY, REBUILD_REASON_KEY,
                                                                        WAS_INTERRUPTED,
                                                                        SEARCH_STATE_FEATURES_DATA_KEY, COLLECTED_RESULTS_DATA_KEY,
                                                                        CONTRIBUTOR_FEATURES_LIST)
  internal val ITEM_SELECTED: VarargEventId = GROUP.registerVarargEvent("item.selected",
                                                                        "An event denoting selection of an item from search results",
                                                                        SESSION_ID, SEARCH_INDEX_DATA_KEY,
                                                                        SELECTED_INDEX, SELECTED_RESULT_ID)

  @VisibleForTesting
  val SESSION_FINISHED: VarargEventId = GROUP.registerVarargEvent("session.finished",
                                                                  "An event denoting finish of a session and closing of a popup",
                                                                  SESSION_ID,
                                                                  SESSION_DURATION, SE_TAB_ID_KEY)
  internal val KEY_NOT_COMPUTED_EVENT = GROUP.registerEvent("key.not.computed",
                                                            SESSION_ID,
                                                            CLASSES_WITHOUT_KEY_PROVIDERS_FIELD,
                                                            "Event sent once per Search Everywhere session with a list of classes for which key was not computed")
  // endregion
}
