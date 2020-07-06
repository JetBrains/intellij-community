// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.experiment

import com.google.gson.Gson
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger

class ClientExperimentStatus : ExperimentStatus {
  companion object {
    private const val EXPERIMENT_GROUP_PROPERTY_KEY = "ml.completion.experiment.group"
    private const val EXPERIMENT_CHANGED_PROPERTY_KEY = "ml.completion.experiment.changed"
    private const val PATH_TO_EXPERIMENT_CONFIG = "/experiment.json"
    private val GSON by lazy { Gson() }
    private val LOG = logger<ClientExperimentStatus>()
    private var experimentChanged: Boolean = false

    fun loadExperimentInfo(): ExperimentInfo {
      try{
        val json = ClientExperimentStatus::class.java.getResource(PATH_TO_EXPERIMENT_CONFIG).readText()
        val experimentInfo = GSON.fromJson(json, ExperimentInfo::class.java)
        checkExperimentGroups(experimentInfo)
        return experimentInfo
      }
      catch (e: Throwable) {
        LOG.error("Error on loading ML Completion experiment info", e)
        return ExperimentInfo.emptyExperiment()
      }
    }

    private fun checkExperimentGroups(experimentInfo: ExperimentInfo) {
      for (group in experimentInfo.groups) {
        if (group.showArrows) assert(group.useMLRanking) { "Showing arrows requires ML ranking" }
        if (group.useMLRanking) assert(group.calculateFeatures) { "ML ranking requires calculating features" }
      }
    }
  }

  private val experimentInfo: ExperimentInfo = loadExperimentInfo()
  private val experimentBucket: Int = EventLogConfiguration.bucket % experimentInfo.experimentBucketsCount
  private val experimentGroup: ExperimentGroupInfo? = experimentInfo.groups.find { it.experimentBucket == experimentBucket }

  init {
    val properties = PropertiesComponent.getInstance()
    val groupNumber = experimentGroup?.number ?: experimentInfo.version
    if (properties.getInt(EXPERIMENT_GROUP_PROPERTY_KEY, experimentInfo.version) != groupNumber) {
      properties.setValue(EXPERIMENT_GROUP_PROPERTY_KEY, groupNumber, experimentInfo.version)
      properties.setValue(EXPERIMENT_CHANGED_PROPERTY_KEY, true)
      experimentChanged = true
    }
  }

  override fun isExperimentOnCurrentIDE(language: Language): Boolean =
    experimentInfo.groups.any { it.number == experimentVersion(language) }

  // later it will support excluding group from experiment for some languages
  override fun experimentVersion(language: Language): Int = experimentGroup?.number ?: experimentInfo.version

  override fun shouldRank(language: Language): Boolean =
    experimentInfo.groups.any { it.number == experimentVersion(language) && it.useMLRanking }

  override fun shouldShowArrows(language: Language): Boolean =
    experimentInfo.groups.any { it.number == experimentVersion(language) && it.showArrows }

  override fun shouldCalculateFeatures(language: Language): Boolean =
    experimentInfo.groups.any { it.number == experimentVersion(language) && it.calculateFeatures }

  override fun experimentChanged(): Boolean {
    if (experimentChanged) {
      val properties = PropertiesComponent.getInstance()
      if (properties.getBoolean(EXPERIMENT_CHANGED_PROPERTY_KEY, false)) {
        properties.setValue(EXPERIMENT_CHANGED_PROPERTY_KEY, false)
        return true
      }
    }
    return false
  }
}