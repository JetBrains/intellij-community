// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import java.util.*

internal class SearchEverywhereMlExperiment {
  companion object {
    private const val NUMBER_OF_GROUPS = 4
  }

  private val isExperimentalMode: Boolean = StatisticsUploadAssistant.isSendAllowed() && ApplicationManager.getApplication().isEAP

  private val tabsWithEnabledLogging = setOf(
    SearchEverywhereTabWithMl.ACTION.tabId,
    SearchEverywhereTabWithMl.FILES.tabId,
    ClassSearchEverywhereContributor::class.java.simpleName,
    SymbolSearchEverywhereContributor::class.java.simpleName,
    SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
  )

  private val tabExperiments = hashMapOf(
    SearchEverywhereTabWithMl.ACTION to Experiment(
      1 to ExperimentType.NO_ML,
      2 to ExperimentType.USE_EXPERIMENTAL_MODEL
    ),

    SearchEverywhereTabWithMl.FILES to Experiment(
      2 to ExperimentType.USE_EXPERIMENTAL_MODEL,
      3 to ExperimentType.NO_ML
    ),

    SearchEverywhereTabWithMl.CLASSES to Experiment(
      2 to ExperimentType.USE_EXPERIMENTAL_MODEL,
    )
  )

  val isAllowed: Boolean
    get() = isExperimentalMode && !Registry.`is`("search.everywhere.force.disable.logging.ml")

  val experimentGroup: Int
    get() = if (isExperimentalMode) {
      val experimentGroup = EventLogConfiguration.getInstance().bucket % NUMBER_OF_GROUPS
      val registryExperimentGroup = Registry.intValue("search.everywhere.ml.experiment.group")
      if (registryExperimentGroup >= 0) registryExperimentGroup else experimentGroup
    }
    else {
      -1
    }

  fun isLoggingEnabledForTab(tabId: String) = tabsWithEnabledLogging.contains(tabId)

  private fun isDisableExperiments(tab: SearchEverywhereTabWithMl): Boolean {
    val key = "search.everywhere.force.disable.experiment.${tab.name.lowercase()}.ml"
    return Registry.`is`(key)
  }

  fun getExperimentForTab(tab: SearchEverywhereTabWithMl): ExperimentType {
    if (!isAllowed || isDisableExperiments(tab)) return ExperimentType.NO_EXPERIMENT
    return tabExperiments[tab]?.getExperimentByGroup(experimentGroup) ?: ExperimentType.NO_EXPERIMENT
  }

  internal enum class ExperimentType {
    NO_EXPERIMENT, NO_ML, USE_EXPERIMENTAL_MODEL
  }

  private class Experiment(vararg experiments: Pair<Int, ExperimentType>) {
    private val tabExperiments: MutableMap<Int, ExperimentType>

    init {
      tabExperiments = hashMapOf(*experiments)
    }

    fun getExperimentByGroup(group: Int) = tabExperiments.getOrDefault(group, ExperimentType.NO_EXPERIMENT)
  }
}

internal class FeaturesLoggingRandomisation {
  private val thresholdsByTab = hashMapOf(
    SearchEverywhereTabWithMl.ACTION.tabId to 1.0,
    SearchEverywhereTabWithMl.FILES.tabId to 0.5,
    ClassSearchEverywhereContributor::class.java.simpleName to 0.5,
    SymbolSearchEverywhereContributor::class.java.simpleName to 1.0,
    SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID to 0.5
  )

  private val seed: Double = Random().nextDouble()

  fun shouldLogFeatures(tabId: String): Boolean = seed < (thresholdsByTab[tabId] ?: 1.0)
}