package com.intellij.searchEverywhereMl

import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment.Companion.VERSION
import com.intellij.searchEverywhereMl.log.MLSE_RECORDER_ID
import com.intellij.util.MathUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/**
 * Handles the machine learning experiments in Search Everywhere.
 * Determines enabled functionality for every user participating in an experiment.
 * Please update the [VERSION] number if you need to reshuffle experiment groups.
 * This might be helpful to avoid the impact of the previous experiments on the current experiments.
 */
class SearchEverywhereMlExperiment {
  companion object {
    const val VERSION = 2
    const val NUMBER_OF_GROUPS = 4
  }

  var isExperimentalMode: Boolean = StatisticsUploadAssistant.isSendAllowed() && ApplicationManager.getApplication().isEAP
    @TestOnly set

  private val tabsWithEnabledLogging = setOf(
    SearchEverywhereTabWithMlRanking.ACTION.tabId,
    SearchEverywhereTabWithMlRanking.FILES.tabId,
    SearchEverywhereTabWithMlRanking.CLASSES.tabId,
    SearchEverywhereTabWithMlRanking.SYMBOLS.tabId,
    SearchEverywhereTabWithMlRanking.ALL.tabId
  )

  private val tabExperiments = hashMapOf(
    SearchEverywhereTabWithMlRanking.ACTION to Experiment(
      1 to ExperimentType.ExactMatchManualFix,
      2 to ExperimentType.NoMl,
      3 to ExperimentType.Typos,
    ),

    SearchEverywhereTabWithMlRanking.FILES to Experiment(
      1 to ExperimentType.SemanticSearch,
      3 to ExperimentType.NoMl
    ),

    SearchEverywhereTabWithMlRanking.CLASSES to Experiment(
      1 to ExperimentType.SemanticSearch,
      3 to ExperimentType.NoMl
    ),

    SearchEverywhereTabWithMlRanking.SYMBOLS to Experiment(
      1 to ExperimentType.SemanticSearch,
    ),

    SearchEverywhereTabWithMlRanking.ALL to Experiment(
      1 to ExperimentType.EssentialContributorPrediction,
      2 to ExperimentType.ExperimentalModel,
    )
  )

  val isAllowed: Boolean
    get() = isExperimentalMode && !Registry.`is`("search.everywhere.force.disable.logging.ml")

  val experimentGroup: Int
    get() = if (isExperimentalMode) {
      val registryExperimentGroup = Registry.intValue("search.everywhere.ml.experiment.group", -1, -1, NUMBER_OF_GROUPS - 1)
      if (registryExperimentGroup >= 0) registryExperimentGroup else computedGroup
    }
    else -1

  private val computedGroup: Int by lazy {
    val mlseLogConfiguration = EventLogConfiguration.getInstance().getOrCreate(MLSE_RECORDER_ID)
    // experiment groups get updated on the VERSION property change:
    MathUtil.nonNegativeAbs((mlseLogConfiguration.deviceId + VERSION).hashCode()) % NUMBER_OF_GROUPS
  }

  fun isLoggingEnabledForTab(tabId: String) = tabsWithEnabledLogging.contains(tabId)

  @Suppress("UnresolvedPluginConfigReference")
  private fun isDisableExperiments(tab: SearchEverywhereTabWithMlRanking): Boolean {
    return Registry.`is`("search.everywhere.force.disable.experiment.${tab.name.lowercase()}.ml")
  }

  fun getExperimentForTab(tab: SearchEverywhereTabWithMlRanking): ExperimentType {
    if (!isAllowed || isDisableExperiments(tab)) {
      return ExperimentType.NoExperiment
    }

    val experimentByGroup = tabExperiments[tab]?.getExperimentByGroup(experimentGroup)
    if (experimentByGroup == null || experimentByGroup == ExperimentType.NoExperiment) {
      return ExperimentType.NoExperiment
    }

    return experimentByGroup
  }

  @TestOnly
  internal fun getTabExperiments(): Map<SearchEverywhereTabWithMlRanking, Experiment> = tabExperiments

  sealed interface ExperimentType {
    /**
     * Indicates that for the current experiment group,
     * there are no active experiments and the default
     * behavior should be followed
     */
    object NoExperiment : ExperimentType

    sealed class ActiveExperiment(val shouldSortByMl: Boolean) : ExperimentType

    /**
     * An experiment where no machine learning
     * should be applied to a tab and old, heuristic-based
     * ranking should be used.
     *
     * This experiment type is used when the machine learning
     * approach became the default for a specific tab,
     * and to gather data how it compares to the old baseline,
     * an experiment with disabled ML can be used.
     */
    object NoMl : ActiveExperiment(false)

    /**
     * An experiment group applicable only for the All tab,
     * where essential contributor prediction will take place.
     */
    object EssentialContributorPrediction : ActiveExperiment(false)

    /**
     * An experiment group where a new (experimental) model
     * should be used instead of the default one.
     *
     * This experiment type is used when the machine learning
     * approach became the default for a specific tab,
     * and a new model should be tested during the A/B testing cycle
     */
    object ExperimentalModel : ActiveExperiment(true)

    /**
     * An experiment group in the Actions tab, that enables typo-tolerant search
     */
    object Typos : ActiveExperiment(true)

    /**
     * An experiment group that enables semantic search
     */
    object SemanticSearch: ActiveExperiment(true)

    /**
     * An experiment group where the exact match issue is manually fixed
     * (see, for example, IJPL-171760 or IJPL-55751)
     */
    object ExactMatchManualFix : ActiveExperiment(true)
  }

  @VisibleForTesting
  internal class Experiment(vararg experiments: Pair<Int, ExperimentType>) {
    val tabExperiments: Map<Int, ExperimentType> = hashMapOf(*experiments)

    fun getExperimentByGroup(group: Int) = tabExperiments.getOrDefault(group, ExperimentType.NoExperiment)
  }
}