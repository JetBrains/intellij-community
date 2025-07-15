@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.ranking.core.id.SearchEverywhereMlItemIdProvider
import kotlin.math.round

internal class SearchEverywhereMlFeaturesCache {
  private val idToElementCacheStorage = hashMapOf<Int, SearchEverywhereMLElementCache>()

  fun getUpdateEventsAndCache(project: Project?,
                              elements: List<SearchEverywhereFoundElementInfoWithMl>,
                              elementIdProvider: SearchEverywhereMlItemIdProvider): List<ObjectEventData>? {
    val actionManager = ActionManager.getInstance()

    return elements.map {
      if (project != null && project.isDisposed) {
        return null
      }

      val elementId = ReadAction.compute<Int?, Nothing> { elementIdProvider.getId(it.element) }

      val elementCache = buildElementCache(it, actionManager, elementId)

      val diffCache = getDiffCacheAndUpdateStorage(elementCache, elementId)
      ObjectEventData(diffCache.toEvents())
    }
  }

  @Synchronized
  private fun getDiffCacheAndUpdateStorage(elementCache: SearchEverywhereMLElementCache, elementId: Int?): SearchEverywhereMLElementCache {
    val prevCache = elementId?.let { idToElementCacheStorage[elementId] }

    return if (elementCache != prevCache) {
      val diffCache = elementCache.getDiff(prevCache)
      elementId?.let {
        idToElementCacheStorage[elementId] = elementCache
      }
      diffCache
    }
    else {
      SearchEverywhereMLElementCache(id = elementId)
    }
  }

  private fun buildElementCache(it: SearchEverywhereFoundElementInfoWithMl,
                                actionManager: ActionManager,
                                elementId: Int?): SearchEverywhereMLElementCache {
    val mlWeight = it.mlWeight ?: -1.0

    return SearchEverywhereMLElementCache(
      contributor = it.contributor,
      id = elementId,
      mlFeatures = it.mlFeatures.ifEmpty { null },
      priority = it.priority,
      mlWeight = if (mlWeight >= 0) roundDouble(mlWeight) else null,
      actionId = getActionIdIfApplicable(it.element, actionManager)
    )
  }

  private fun getActionIdIfApplicable(element: Any, actionManager: ActionManager): String? {
    return if ((element is GotoActionModel.MatchedValue) && (element.value is GotoActionModel.ActionWrapper)) {
      val action = (element.value as GotoActionModel.ActionWrapper).action
      return actionManager.getId(action) ?: action.javaClass.name
    }
    else {
      null
    }
  }

  private fun roundDouble(value: Double): Double {
    if (!value.isFinite()) return -1.0
    return round(value * 100000) / 100000
  }
}


internal data class SearchEverywhereMLElementCache(
  val contributor: SearchEverywhereContributor<*>? = null,
  val id: Int? = null,
  val mlFeatures: List<EventPair<*>>? = null,
  val mlWeight: Double? = null,
  val priority: Int? = null,
  val actionId: String? = null,
  val absentFeatures: List<String>? = null
) {
  fun toEvents(): List<EventPair<*>> {
    val result = mutableListOf<EventPair<*>>()
    contributor?.let { result.add(SearchEverywhereMLStatisticsCollector.ELEMENT_CONTRIBUTOR.with(contributor.searchProviderId)) }
    id?.let { result.add(SearchEverywhereMLStatisticsCollector.ID_KEY.with(it)) }
    mlWeight?.let { result.add(SearchEverywhereMLStatisticsCollector.ML_WEIGHT_KEY.with(it)) }
    priority?.let { result.add(SearchEverywhereMLStatisticsCollector.PRIORITY_KEY.with(it))}
    mlFeatures?.let { result.add(SearchEverywhereMLStatisticsCollector.FEATURES_DATA_KEY.with(ObjectEventData(it))) }
    actionId?.let { result.add(SearchEverywhereMLStatisticsCollector.ACTION_ID_KEY.with(it)) }
    absentFeatures?.let { result.add(SearchEverywhereMLStatisticsCollector.ABSENT_FEATURES_KEY.with(it)) }
    return result
  }


  fun getDiff(prevCache: SearchEverywhereMLElementCache?): SearchEverywhereMLElementCache {
    if (contributor != prevCache?.contributor) {
      return this
    }

    val updateWeight = if (mlWeight != prevCache?.mlWeight) mlWeight else null

    val updatePriority = if (priority != prevCache?.priority) priority else null

    val updateActionId = if (actionId != prevCache?.actionId) actionId else null

    val updateFeatures = mlFeatures?.minus((prevCache?.mlFeatures ?: emptyList()).toSet())

    val absentFeatures = prevCache?.mlFeatures?.map { it.field.name }?.minus((mlFeatures?.map { it.field.name } ?: emptyList()).toSet())

    return SearchEverywhereMLElementCache(
      id = id,
      contributor = null,
      mlFeatures = updateFeatures,
      mlWeight = updateWeight,
      priority = updatePriority,
      actionId = updateActionId,
      absentFeatures = absentFeatures?.ifEmpty { null }
    )
  }
}