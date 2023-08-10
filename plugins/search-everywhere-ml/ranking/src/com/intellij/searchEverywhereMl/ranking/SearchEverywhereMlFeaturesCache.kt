package com.intellij.searchEverywhereMl.ranking

import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.ranking.id.SearchEverywhereMlItemIdProvider
import kotlin.math.round

internal class SearchEverywhereMlFeaturesCache {
  private val idToElementCacheStorage = hashMapOf<Int, SearchEverywhereMLElementCache>()

  fun getUpdateEventsAndCache(project: Project?, shouldLogFeatures: Boolean,
                              elements: List<SearchEverywhereFoundElementInfoWithMl>,
                              contributorFeaturesProvider: (SearchEverywhereFoundElementInfoWithMl) -> List<EventPair<*>>,
                              elementIdProvider: SearchEverywhereMlItemIdProvider): List<ObjectEventData>? {
    val actionManager = ActionManager.getInstance()

    return elements.map {
      if (project != null && project.isDisposed) {
        return null
      }

      val elementId = elementIdProvider.getId(it.element)

      val elementCache = buildElementCache(it, shouldLogFeatures, actionManager, contributorFeaturesProvider(it), elementId)

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
                                shouldLogFeatures: Boolean,
                                actionManager: ActionManager,
                                contributorFeatures: List<EventPair<*>>,
                                elementId: Int?): SearchEverywhereMLElementCache {
    val mlWeight = it.mlWeight ?: -1.0

    if (shouldLogFeatures) {
      return SearchEverywhereMLElementCache(
        contributor = contributorFeatures,
        id = elementId,
        mlFeatures = it.mlFeatures.ifEmpty { null },
        mlWeight = if (mlWeight >= 0) roundDouble(mlWeight) else null,
        actionId = getActionIdIfApplicable(it.element, actionManager)
      )
    }
    else {
      return SearchEverywhereMLElementCache(
        contributor = contributorFeatures,
        id = elementId,
      )
    }
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
  val contributor: List<EventPair<*>>? = null,
  val id: Int? = null,
  val mlFeatures: List<EventPair<*>>? = null,
  val mlWeight: Double? = null,
  val actionId: String? = null,
  val absentFeatures: List<String>? = null
) {
  fun toEvents(): List<EventPair<*>> {
    val result = mutableListOf<EventPair<*>>()
    contributor?.let { result.add(SearchEverywhereMLStatisticsCollector.CONTRIBUTOR_DATA_KEY.with(ObjectEventData(it))) }
    id?.let { result.add(SearchEverywhereMLStatisticsCollector.ID_KEY.with(it)) }
    mlWeight?.let { result.add(SearchEverywhereMLStatisticsCollector.ML_WEIGHT_KEY.with(it)) }
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

    val updateActionId = if (actionId != prevCache?.actionId) actionId else null

    val updateFeatures = mlFeatures?.minus((prevCache?.mlFeatures ?: emptyList()).toSet())

    val absentFeatures = prevCache?.mlFeatures?.map { it.field.name }?.minus((mlFeatures?.map { it.field.name } ?: emptyList()).toSet())

    return SearchEverywhereMLElementCache(
      id = id,
      contributor = null,
      mlFeatures = updateFeatures,
      mlWeight = updateWeight,
      actionId = updateActionId,
      absentFeatures = absentFeatures?.ifEmpty { null }
    )
  }
}