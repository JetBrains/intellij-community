package com.intellij.searchEverywhereMl

import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.settings.SearchEverywhereMlSettings
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

class SearchEverywhereMlExperiment {
  companion object {
    const val NUMBER_OF_GROUPS = 4
  }

  var isExperimentalMode: Boolean = StatisticsUploadAssistant.isSendAllowed() && ApplicationManager.getApplication().isEAP
    @TestOnly set

  private val tabsWithEnabledLogging = setOf(
    SearchEverywhereTabWithMlRanking.ACTION.tabId,
    SearchEverywhereTabWithMlRanking.FILES.tabId,
    SearchEverywhereTabWithMlRanking.CLASSES.tabId,
    SymbolSearchEverywhereContributor::class.java.simpleName,
    SearchEverywhereTabWithMlRanking.ALL.tabId
  )

  private val tabExperiments = hashMapOf(
    SearchEverywhereTabWithMlRanking.ACTION to Experiment(
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
    NO_EXPERIMENT, NO_ML, USE_EXPERIMENTAL_MODEL, NO_ML_FEATURES, ENABLE_TYPOS, NO_RECENT_FILES_PRIORITIZATION
  }

  @VisibleForTesting
  internal class Experiment(vararg experiments: Pair<Int, ExperimentType>) {
    val tabExperiments: Map<Int, ExperimentType> = hashMapOf(*experiments)

    fun getExperimentByGroup(group: Int) = tabExperiments.getOrDefault(group, ExperimentType.NO_EXPERIMENT)
  }
}