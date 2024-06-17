// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.marketplaceMl

import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.mp.MP_RECORDER_ID
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.MathUtil
import org.jetbrains.annotations.TestOnly

object MarketplaceMLExperiment {
  const val VERSION = 0
  const val NUMBER_OF_GROUPS = 4

  enum class ExperimentOption { NO_ML, USE_ML }

  private val defaultExperimentOption = ExperimentOption.NO_ML
  private val marketplaceExperiments = mapOf(
    0 to ExperimentOption.USE_ML,
  )

  val experimentGroup: Int
    get() = if (isExperimentalMode) {
      val registryExperimentGroup = Registry.intValue("marketplace.ml.ranking.experiment.group", -1, -1, NUMBER_OF_GROUPS - 1)
      if (registryExperimentGroup >= 0) registryExperimentGroup else computedGroup
    }
    else -1

  var isExperimentalMode: Boolean = StatisticsUploadAssistant.isSendAllowed() && ApplicationManager.getApplication().isEAP
    @TestOnly set

  private val computedGroup: Int by lazy {
    val mpLogConfiguration = EventLogConfiguration.getInstance().getOrCreate(MP_RECORDER_ID)
    // experiment groups get updated on the VERSION property change:
    MathUtil.nonNegativeAbs((mpLogConfiguration.deviceId + VERSION).hashCode()) % NUMBER_OF_GROUPS
  }

  fun getExperiment(): ExperimentOption {
    return if (Registry.`is`("marketplace.ml.ranking.disable.experiments")) defaultExperimentOption
    else marketplaceExperiments.getOrDefault(experimentGroup, defaultExperimentOption)
  }
}