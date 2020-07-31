// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.experiment

import com.google.gson.Gson
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger

class ClientExperimentStatus : ExperimentStatus {
  companion object {
    private const val EXPERIMENT_GROUP_PROPERTY_KEY = "ml.completion.experiment.group"
    private const val PATH_TO_EXPERIMENT_CONFIG = "/experiment.json"
    private val GSON by lazy { Gson() }
    private val LOG = logger<ClientExperimentStatus>()

    fun loadExperimentInfo(): ExperimentConfig {
      try{
        val json = ClientExperimentStatus::class.java.getResource(PATH_TO_EXPERIMENT_CONFIG).readText()
        val experimentInfo = GSON.fromJson(json, ExperimentConfig::class.java)
        checkExperimentGroups(experimentInfo)
        return experimentInfo
      }
      catch (e: Throwable) {
        LOG.error("Error on loading ML Completion experiment info", e)
        return ExperimentConfig.disabledExperiment()
      }
    }

    private fun checkExperimentGroups(experimentInfo: ExperimentConfig) {
      for (group in experimentInfo.groups) {
        if (group.showArrows) assert(group.useMLRanking) { "Showing arrows requires ML ranking" }
        if (group.useMLRanking) assert(group.calculateFeatures) { "ML ranking requires calculating features" }
      }
      for (language in experimentInfo.languages) {
        assert(language.includeGroups.size <= language.experimentBucketsCount)
        { "Groups count must be less than the total number of buckets (${language.id})" }
        assert(language.includeGroups.all { number ->
          experimentInfo.groups.any { it.number == number }
        }) { "Group included for language (${language.id}) should be among general list of groups" }
      }
    }
  }

  private val experimentConfig: ExperimentConfig = loadExperimentInfo()
  private val language2group: MutableMap<String, ExperimentInfo> = mutableMapOf()
  private val language2experimentChanged: MutableMap<String, Boolean> = mutableMapOf()

  init {
    val properties = PropertiesComponent.getInstance()
    for (languageSettings in experimentConfig.languages) {
      val bucket = EventLogConfiguration.bucket % languageSettings.experimentBucketsCount
      val groupNumber = if (languageSettings.includeGroups.size > bucket) languageSettings.includeGroups[bucket] else experimentConfig.version
      val group = experimentConfig.groups.find { it.number == groupNumber }
      val groupInfo = if (group == null) ExperimentInfo(false, experimentConfig.version)
      else ExperimentInfo(true, group.number, group.useMLRanking, group.showArrows, group.calculateFeatures)
      language2group[languageSettings.id] = groupInfo
      val experimentChanged = properties.getInt(experimentChangedProperty(languageSettings.id), experimentConfig.version) != groupNumber
      language2experimentChanged[languageSettings.id] = experimentChanged
    }
  }

  override fun forLanguage(language: Language): ExperimentInfo {
    if (ApplicationManager.getApplication().isUnitTestMode) return ExperimentInfo(false, 0)
    val matchingLanguage = findMatchingLanguage(language) ?: return ExperimentInfo(
      false, experimentConfig.version)
    return language2group[matchingLanguage] ?: ExperimentInfo(false, experimentConfig.version)
  }

  override fun experimentChanged(language: Language): Boolean {
    val matchingLanguage = findMatchingLanguage(language) ?: return false
    val experimentChanged = language2experimentChanged[matchingLanguage] ?: return false
    if (experimentChanged) {
      val groupNumber = language2group[matchingLanguage]?.version ?: experimentConfig.version
      PropertiesComponent.getInstance().setValue(experimentChangedProperty(matchingLanguage), groupNumber, experimentConfig.version)
      language2experimentChanged[matchingLanguage] = false
    }
    return experimentChanged
  }

  private fun experimentChangedProperty(languageId: String) = "$EXPERIMENT_GROUP_PROPERTY_KEY.$languageId"

  private fun findMatchingLanguage(language: Language): String? {
    val baseLanguages = Language.getRegisteredLanguages().filter { language.isKindOf(it) }
    return language2group.keys.find { languageId ->
      baseLanguages.any { languageId.equals(it.id, ignoreCase = true) }
    }
  }
}