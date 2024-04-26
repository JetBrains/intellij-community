package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.internal.statistic.eventLog.events.EventPair


/**
 * This class is the same as SearchEverywhereFoundElementInfoWithMl, has the same constructor parameters but different super() call.
 * It is used to store ml information about element and the intention to calculate orderingDiff for this element and transform it to
 * SearchEverywhereFoundElementInfoAfterDiff
 */
internal open class SearchEverywhereFoundElementInfoBeforeDiff(
  element: Any,
  val heuristicPriority: Int,
  contributor: SearchEverywhereContributor<*>,
  val mlWeight: Double?,
  val mlFeatures: List<EventPair<*>>
) : SearchEverywhereFoundElementInfo(element, heuristicPriority, contributor)

/**
 * This class is used to display orderingDiff information and store seSearchPosition for OpenFeaturesInScratchFileAction
 */
internal open class SearchEverywhereFoundElementInfoAfterDiff(
  element: Any,
  heuristicPriority: Int,
  contributor: SearchEverywhereContributor<*>,
  mlWeight: Double?,
  mlFeatures: List<EventPair<*>>,
  val orderingDiff: Int,
  val seSearchPosition: Int
) : SearchEverywhereFoundElementInfoWithMl(element, heuristicPriority, contributor, mlWeight, mlFeatures)