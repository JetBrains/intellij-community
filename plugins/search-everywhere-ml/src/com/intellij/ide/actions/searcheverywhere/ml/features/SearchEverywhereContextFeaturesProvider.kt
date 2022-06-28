// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

internal class SearchEverywhereContextFeaturesProvider {
  companion object {
    internal val LOCAL_MAX_USAGE_COUNT_KEY = EventFields.Int("maxUsage")
    internal val LOCAL_MIN_USAGE_COUNT_KEY = EventFields.Int("minUsage")
    internal val LOCAL_MAX_USAGE_SE_COUNT_KEY = EventFields.Int("maxUsageSE")
    internal val LOCAL_MIN_USAGE_SE_COUNT_KEY = EventFields.Int("minUsageSE")

    internal val GLOBAL_MAX_USAGE_COUNT_KEY = EventFields.Long("globalMaxUsage")
    internal val GLOBAL_MIN_USAGE_COUNT_KEY = EventFields.Long("globalMinUsage")
    private val versionPattern = "V${ActionsGlobalSummaryManager.getUpdatedStatisticsVersion()}"
    internal val GLOBAL_MAX_USAGE_COUNT_KEY_VERSIONED = EventFields.Long("globalMaxUsage$versionPattern")
    internal val GLOBAL_MIN_USAGE_COUNT_KEY_VERSIONED = EventFields.Long("globalMinUsage$versionPattern")

    internal val OPEN_FILE_TYPES_KEY = EventFields.StringListValidatedByCustomRule("openFileTypes", "file_type")
    internal val NUMBER_OF_OPEN_EDITORS_KEY = EventFields.Int("numberOfOpenEditors")
    internal val IS_SINGLE_MODULE_PROJECT = EventFields.Boolean("isSingleModuleProject")

    internal fun getContextFields(): List<EventField<*>> {
      return arrayListOf(
        LOCAL_MAX_USAGE_COUNT_KEY, LOCAL_MIN_USAGE_COUNT_KEY, LOCAL_MAX_USAGE_SE_COUNT_KEY, LOCAL_MIN_USAGE_SE_COUNT_KEY,
        GLOBAL_MAX_USAGE_COUNT_KEY, GLOBAL_MIN_USAGE_COUNT_KEY, GLOBAL_MAX_USAGE_COUNT_KEY_VERSIONED, GLOBAL_MIN_USAGE_COUNT_KEY_VERSIONED,
        OPEN_FILE_TYPES_KEY, NUMBER_OF_OPEN_EDITORS_KEY, IS_SINGLE_MODULE_PROJECT
      )
    }
  }

  fun getContextFeatures(project: Project?): List<EventPair<*>> {
    val data = arrayListOf<EventPair<*>>()
    val localTotalStats = ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java).getTotalStats()
    
    val globalSummary = ApplicationManager.getApplication().getService(ActionsGlobalSummaryManager::class.java)

    val globalTotalStats = globalSummary.totalSummary
    val updatedGlobalTotalStats = globalSummary.updatedTotalSummary

    data.add(LOCAL_MAX_USAGE_COUNT_KEY.with(localTotalStats.maxUsageCount))
    data.add(LOCAL_MIN_USAGE_COUNT_KEY.with(localTotalStats.minUsageCount))
    data.add(LOCAL_MAX_USAGE_SE_COUNT_KEY.with(localTotalStats.maxUsageFromSearchEverywhere))
    data.add(LOCAL_MIN_USAGE_SE_COUNT_KEY.with(localTotalStats.minUsageFromSearchEverywhere))
    data.add(GLOBAL_MAX_USAGE_COUNT_KEY.with(globalTotalStats.maxUsageCount))
    data.add(GLOBAL_MIN_USAGE_COUNT_KEY.with(globalTotalStats.minUsageCount))
    data.add(GLOBAL_MAX_USAGE_COUNT_KEY_VERSIONED.with(updatedGlobalTotalStats.maxUsageCount))
    data.add(GLOBAL_MIN_USAGE_COUNT_KEY_VERSIONED.with(updatedGlobalTotalStats.minUsageCount))

    project?.let {
      if (project.isDisposed) {
        return@let
      }

      val fem = FileEditorManager.getInstance(it)
      data.add(OPEN_FILE_TYPES_KEY.with(fem.openFiles.map { file -> file.fileType.name }.distinct()))
      data.add(NUMBER_OF_OPEN_EDITORS_KEY.with(fem.allEditors.size))
      data.add(IS_SINGLE_MODULE_PROJECT.with(ModuleManager.getInstance(it).modules.size == 1))
    }

    return data
  }
}