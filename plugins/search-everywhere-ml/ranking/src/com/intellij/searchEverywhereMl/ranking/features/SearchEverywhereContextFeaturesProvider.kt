// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector
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

    private val GLOBAL_STATISTICS_CONTEXT_DEFAULT = GlobalStatisticsContextFields(ActionsGlobalSummaryManager.getDefaultStatisticsVersion())
    private val GLOBAL_STATISTICS_CONTEXT_UPDATED = GlobalStatisticsContextFields(ActionsGlobalSummaryManager.getUpdatedStatisticsVersion())


    internal val OPEN_FILE_TYPES_KEY = EventFields.StringListValidatedByCustomRule("openFileTypes", FileTypeUsagesCollector.ValidationRule::class.java)
    internal val NUMBER_OF_OPEN_EDITORS_KEY = EventFields.Int("numberOfOpenEditors")
    internal val IS_SINGLE_MODULE_PROJECT = EventFields.Boolean("isSingleModuleProject")

    internal fun getContextFields(): List<EventField<*>> {
      val fields = arrayListOf<EventField<*>>(
        LOCAL_MAX_USAGE_COUNT_KEY, LOCAL_MIN_USAGE_COUNT_KEY, LOCAL_MAX_USAGE_SE_COUNT_KEY, LOCAL_MIN_USAGE_SE_COUNT_KEY,
        OPEN_FILE_TYPES_KEY, NUMBER_OF_OPEN_EDITORS_KEY, IS_SINGLE_MODULE_PROJECT
      )
      fields.addAll(GLOBAL_STATISTICS_CONTEXT_DEFAULT.getFieldsDeclaration() +
                    GLOBAL_STATISTICS_CONTEXT_UPDATED.getFieldsDeclaration())
      return fields
    }
  }

  fun getContextFeatures(project: Project?): List<EventPair<*>> {
    val data = arrayListOf<EventPair<*>>()
    val localTotalStats = ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java).getTotalStats()
    data.add(LOCAL_MAX_USAGE_COUNT_KEY.with(localTotalStats.maxUsageCount))
    data.add(LOCAL_MIN_USAGE_COUNT_KEY.with(localTotalStats.minUsageCount))
    data.add(LOCAL_MAX_USAGE_SE_COUNT_KEY.with(localTotalStats.maxUsageFromSearchEverywhere))
    data.add(LOCAL_MIN_USAGE_SE_COUNT_KEY.with(localTotalStats.minUsageFromSearchEverywhere))

    val globalSummary = ApplicationManager.getApplication().getService(ActionsGlobalSummaryManager::class.java)
    data.addAll(GLOBAL_STATISTICS_CONTEXT_DEFAULT.getGlobalUsageStatistics(globalSummary.totalSummary) +
                GLOBAL_STATISTICS_CONTEXT_UPDATED.getGlobalUsageStatistics(globalSummary.updatedTotalSummary))

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

  internal class GlobalStatisticsContextFields(version: Int) {
    private val globalMaxUsage = EventFields.Long("globalMaxUsageV${version}")
    private val globalMinUsage = EventFields.Long("globalMinUsageV${version}")

    fun getFieldsDeclaration(): List<EventField<*>> = arrayListOf(globalMaxUsage, globalMinUsage)

    fun getGlobalUsageStatistics(stats: ActionsGlobalSummaryManager.ActionsGlobalTotalSummary): List<EventPair<*>> {
      return arrayListOf<EventPair<*>>(
        globalMaxUsage.with(stats.maxUsageCount()),
        globalMinUsage.with(stats.minUsageCount())
      )
    }
  }
}