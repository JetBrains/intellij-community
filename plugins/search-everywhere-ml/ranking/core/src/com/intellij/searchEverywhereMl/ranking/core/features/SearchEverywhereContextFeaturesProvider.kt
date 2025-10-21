// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.internal.statistic.local.ContributorsGlobalSummaryManager
import com.intellij.internal.statistic.local.ContributorsLocalSummary
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.ranking.core.features.statistician.ContributorsLocalStatisticsContextFields

internal class SearchEverywhereContextFeaturesProvider {
  companion object {
    internal val LOCAL_MAX_USAGE_COUNT_KEY = EventFields.Int("maxUsage")
    internal val LOCAL_MIN_USAGE_COUNT_KEY = EventFields.Int("minUsage")
    internal val LOCAL_MAX_USAGE_SE_COUNT_KEY = EventFields.Int("maxUsageSE")
    internal val LOCAL_MIN_USAGE_SE_COUNT_KEY = EventFields.Int("minUsageSE")

    private val ACTIONS_GLOBAL_STATISTICS_CONTEXT_DEFAULT = ActionsGlobalStatisticsContextFields(ActionsGlobalSummaryManager.STATISTICS_VERSION)
    private val ACTIONS_GLOBAL_STATISTICS_CONTEXT_UPDATED = ActionsGlobalStatisticsContextFields(ActionsGlobalSummaryManager.UPDATED_STATISTICS_VERSION)
    private val CONTRIBUTORS_GLOBAL_STATISTICS_CONTEXT = ContributorsGlobalStatisticsContextFields()
    private val CONTRIBUTORS_LOCAL_STATISTICS_CONTEXT = ContributorsLocalStatisticsContextFields()


    internal val OPEN_FILE_TYPES_KEY = EventFields.StringListValidatedByCustomRule("openFileTypes", FileTypeUsagesCollector.ValidationRule::class.java)
    internal val NUMBER_OF_OPEN_EDITORS_KEY = EventFields.Int("numberOfOpenEditors")
    internal val IS_SINGLE_MODULE_PROJECT = EventFields.Boolean("isSingleModuleProject")

    internal fun getContextFields(): List<EventField<*>> {
      return buildList {
        addAll(listOf(
          LOCAL_MAX_USAGE_COUNT_KEY, LOCAL_MIN_USAGE_COUNT_KEY, LOCAL_MAX_USAGE_SE_COUNT_KEY, LOCAL_MIN_USAGE_SE_COUNT_KEY,
          OPEN_FILE_TYPES_KEY, NUMBER_OF_OPEN_EDITORS_KEY, IS_SINGLE_MODULE_PROJECT
        ))

        addAll(CONTRIBUTORS_LOCAL_STATISTICS_CONTEXT.getFieldsDeclaration())
        addAll(ACTIONS_GLOBAL_STATISTICS_CONTEXT_DEFAULT.getFieldsDeclaration())
        addAll(ACTIONS_GLOBAL_STATISTICS_CONTEXT_UPDATED.getFieldsDeclaration())
        addAll(CONTRIBUTORS_GLOBAL_STATISTICS_CONTEXT.getFieldsDeclaration())
      }
    }
  }

  fun getContextFeatures(project: Project?): List<EventPair<*>> {
    val localTotalStats = ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java).getTotalStats()

    return buildList {
      add(LOCAL_MAX_USAGE_COUNT_KEY.with(localTotalStats.maxUsageCount))
      add(LOCAL_MIN_USAGE_COUNT_KEY.with(localTotalStats.minUsageCount))
      add(LOCAL_MAX_USAGE_SE_COUNT_KEY.with(localTotalStats.maxUsageFromSearchEverywhere))
      add(LOCAL_MIN_USAGE_SE_COUNT_KEY.with(localTotalStats.minUsageFromSearchEverywhere))

      val contributorsSelectionsRange = ContributorsLocalSummary.getInstance().getContributorsSelectionsRange()
      addAll(CONTRIBUTORS_LOCAL_STATISTICS_CONTEXT.getLocalContextStatistics(contributorsSelectionsRange))

      val actionsGlobalSummary = ActionsGlobalSummaryManager.getInstance()
      val contributorsGlobalSummary = ContributorsGlobalSummaryManager.getInstance()
      addAll(ACTIONS_GLOBAL_STATISTICS_CONTEXT_DEFAULT.getGlobalContextStatistics(actionsGlobalSummary.eventCountRange) +
             ACTIONS_GLOBAL_STATISTICS_CONTEXT_UPDATED.getGlobalContextStatistics(actionsGlobalSummary.updatedEventCountRange) +
             CONTRIBUTORS_GLOBAL_STATISTICS_CONTEXT.getGlobalContextStatistics(contributorsGlobalSummary.eventCountRange)
      )

      project?.let {
        if (project.isDisposed) {
          return@let
        }

        val fem = FileEditorManager.getInstance(it)
        add(OPEN_FILE_TYPES_KEY.with(fem.openFiles.map { file -> file.fileType.name }.distinct()))
        add(NUMBER_OF_OPEN_EDITORS_KEY.with(fem.allEditors.size))
        add(IS_SINGLE_MODULE_PROJECT.with(ModuleManager.getInstance(it).modules.size == 1))
      }
    }
  }
}