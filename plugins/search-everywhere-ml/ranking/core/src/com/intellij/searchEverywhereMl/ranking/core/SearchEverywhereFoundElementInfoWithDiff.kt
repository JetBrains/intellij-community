package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.internal.statistic.eventLog.events.EventPair


/**
 * This class is the same as SearchEverywhereFoundElementInfoWithMl, has the same constructor parameters but different super() call.
 * It is used to store ml information about element and the intention to calculate orderingDiff for this element and transform it to
 * SearchEverywhereFoundElementInfoAfterDiff
 */
internal open class SearchEverywhereFoundElementInfoBeforeDiff(
  element: Any,
  val elementId: Int?,
  val heuristicPriority: Int,
  contributor: SearchEverywhereContributor<*>,
  val mlWeight: Double?,
  val mlFeatures: List<EventPair<*>>,
  correction: SearchEverywhereSpellCheckResult = SearchEverywhereSpellCheckResult.NoCorrection
) : SearchEverywhereFoundElementInfo(element, heuristicPriority, contributor, correction)

/**
 * This class is used to display orderingDiff information and store seSearchPosition for OpenFeaturesInScratchFileAction
 */
internal open class SearchEverywhereFoundElementInfoAfterDiff(
  element: Any,
  elementId: Int?,
  heuristicPriority: Int,
  contributor: SearchEverywhereContributor<*>,
  mlWeight: Double?,
  mlFeatures: List<EventPair<*>>,
  correction: SearchEverywhereSpellCheckResult = SearchEverywhereSpellCheckResult.NoCorrection,
  val orderingDiff: Int,
  val seSearchPosition: Int
) : SearchEverywhereFoundElementInfoWithMl(element, elementId, heuristicPriority, contributor, mlWeight, mlFeatures, correction)