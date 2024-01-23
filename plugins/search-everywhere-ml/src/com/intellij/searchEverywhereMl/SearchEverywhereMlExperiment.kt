package com.intellij.searchEverywhereMl

import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.log.MLSE_RECORDER_ID
import com.intellij.searchEverywhereMl.settings.SearchEverywhereMlSettings
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
    const val VERSION = 1
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
      1 to ExperimentType.ENABLE_SEMANTIC_SEARCH,
      2 to ExperimentType.USE_EXPERIMENTAL_MODEL,
      3 to ExperimentType.ENABLE_TYPOS,
    ),

    SearchEverywhereTabWithMlRanking.FILES to Experiment(
      2 to ExperimentType.USE_EXPERIMENTAL_MODEL,
      3 to ExperimentType.NO_ML
    ),

    SearchEverywhereTabWithMlRanking.CLASSES to Experiment(
      2 to ExperimentType.USE_EXPERIMENTAL_MODEL,
      3 to ExperimentType.NO_ML
    ),

    SearchEverywhereTabWithMlRanking.ALL to Experiment(
      2 to ExperimentType.USE_EXPERIMENTAL_MODEL,
      3 to ExperimentType.NO_RECENT_FILES_PRIORITIZATION
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

  private fun isDisableExperiments(tab: SearchEverywhereTabWithMlRanking): Boolean {
    return Registry.`is`("search.everywhere.force.disable.experiment.${tab.name.lowercase()}.ml")
  }

  fun getExperimentForTab(tab: SearchEverywhereTabWithMlRanking): ExperimentType {
    val settings = service<SearchEverywhereMlSettings>()
    if (!isAllowed || isDisableExperiments(tab)) {
      settings.disableExperiment(tab)
      return ExperimentType.NO_EXPERIMENT
    }

    val experimentByGroup = tabExperiments[tab]?.getExperimentByGroup(experimentGroup)
    if (experimentByGroup == null || experimentByGroup == ExperimentType.NO_EXPERIMENT) {
      settings.disableExperiment(tab)
      return ExperimentType.NO_EXPERIMENT
    }

    val enabledMlRanking = experimentByGroup != ExperimentType.NO_ML && experimentByGroup != ExperimentType.NO_ML_FEATURES
    val isExperimentAllowed = settings.updateExperimentStateIfAllowed(tab, enabledMlRanking)
    return if (isExperimentAllowed) experimentByGroup else ExperimentType.NO_EXPERIMENT
  }

  @TestOnly
  internal fun getTabExperiments(): Map<SearchEverywhereTabWithMlRanking, Experiment> = tabExperiments

  enum class ExperimentType {
    NO_EXPERIMENT, NO_ML, USE_EXPERIMENTAL_MODEL, NO_ML_FEATURES, ENABLE_TYPOS,
    NO_RECENT_FILES_PRIORITIZATION, ENABLE_SEMANTIC_SEARCH
  }

  @VisibleForTesting
  internal class Experiment(vararg experiments: Pair<Int, ExperimentType>) {
    val tabExperiments: Map<Int, ExperimentType> = hashMapOf(*experiments)

    fun getExperimentByGroup(group: Int) = tabExperiments.getOrDefault(group, ExperimentType.NO_EXPERIMENT)
  }
}