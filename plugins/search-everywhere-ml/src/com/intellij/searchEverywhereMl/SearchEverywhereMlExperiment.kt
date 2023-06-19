package com.intellij.searchEverywhereMl

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.TestOnly

class SearchEverywhereMlExperiment {
  companion object {
    const val NUMBER_OF_GROUPS = 4
  }

  var isExperimentalMode: Boolean = StatisticsUploadAssistant.isSendAllowed() && ApplicationManager.getApplication().isEAP
    @TestOnly set

  private val tabsWithEnabledLogging = setOf(
    SearchEverywhereTabWithMlRanking.ACTION.tabId,
    SearchEverywhereTabWithMlRanking.FILES.tabId,
    ClassSearchEverywhereContributor::class.java.simpleName,
    SymbolSearchEverywhereContributor::class.java.simpleName,
    SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
  )

  private val tabExperiments = hashMapOf(
    SearchEverywhereTabWithMlRanking.ACTION to Experiment(
      1 to ExperimentType.NO_ML,
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
    )
  )

  val isAllowed: Boolean
    get() = isExperimentalMode && !Registry.`is`("search.everywhere.force.disable.logging.ml")

  val experimentGroup: Int
    get() = if (isExperimentalMode) {
      val experimentGroup = EventLogConfiguration.getInstance().bucket % NUMBER_OF_GROUPS
      val registryExperimentGroup = Registry.intValue("search.everywhere.ml.experiment.group", -1, -1, NUMBER_OF_GROUPS - 1)
      if (registryExperimentGroup >= 0) registryExperimentGroup else experimentGroup
    }
    else {
      -1
    }

  fun isLoggingEnabledForTab(tabId: String) = tabsWithEnabledLogging.contains(tabId)

  private fun isDisableExperiments(tab: SearchEverywhereTabWithMlRanking): Boolean {
    val key = "search.everywhere.force.disable.experiment.${tab.name.lowercase()}.ml"
    return Registry.`is`(key)
  }

  fun getExperimentForTab(tab: SearchEverywhereTabWithMlRanking): ExperimentType {
    if (!isAllowed || isDisableExperiments(tab)) return ExperimentType.NO_EXPERIMENT
    return tabExperiments[tab]?.getExperimentByGroup(experimentGroup) ?: ExperimentType.NO_EXPERIMENT
  }

  enum class ExperimentType {
    NO_EXPERIMENT, NO_ML, USE_EXPERIMENTAL_MODEL, NO_ML_FEATURES, ENABLE_TYPOS
  }

  private class Experiment(vararg experiments: Pair<Int, ExperimentType>) {
    private val tabExperiments: MutableMap<Int, ExperimentType>

    init {
      tabExperiments = hashMapOf(*experiments)
    }

    fun getExperimentByGroup(group: Int) = tabExperiments.getOrDefault(group, ExperimentType.NO_EXPERIMENT)
  }
}