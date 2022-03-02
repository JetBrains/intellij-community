// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

internal class SearchEverywhereContextFeaturesProvider {
  companion object {
    private const val LOCAL_MAX_USAGE_COUNT_KEY = "maxUsage"
    private const val LOCAL_MIN_USAGE_COUNT_KEY = "minUsage"
    private const val LOCAL_MAX_USAGE_SE_COUNT_KEY = "maxUsageSE"
    private const val LOCAL_MIN_USAGE_SE_COUNT_KEY = "minUsageSE"
    private const val GLOBAL_MAX_USAGE_COUNT_KEY = "globalMaxUsage"
    private const val GLOBAL_MIN_USAGE_COUNT_KEY = "globalMinUsage"

    private const val OPEN_FILE_TYPES_KEY = "openFileTypes"
    private const val NUMBER_OF_OPEN_EDITORS_KEY = "numberOfOpenEditors"
    private const val IS_SINGLE_MODULE_PROJECT = "isSingleModuleProject"
  }

  fun getContextFeatures(project: Project?): Map<String, Any> {
    val data = hashMapOf<String, Any>()
    val localTotalStats = ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java).getTotalStats()
    
    val globalSummary = ApplicationManager.getApplication().getService(ActionsGlobalSummaryManager::class.java)

    val globalTotalStats = globalSummary.totalSummary
    val updatedGlobalTotalStats = globalSummary.updatedTotalSummary

    val updatedGlobalStatsVersion = globalSummary.updatedStatisticsVersion
    val versionPattern = "V$updatedGlobalStatsVersion"

    data[LOCAL_MAX_USAGE_COUNT_KEY] = localTotalStats.maxUsageCount
    data[LOCAL_MIN_USAGE_COUNT_KEY] = localTotalStats.minUsageCount
    data[LOCAL_MAX_USAGE_SE_COUNT_KEY] = localTotalStats.maxUsageFromSearchEverywhere
    data[LOCAL_MIN_USAGE_SE_COUNT_KEY] = localTotalStats.minUsageFromSearchEverywhere
    data[GLOBAL_MAX_USAGE_COUNT_KEY] = globalTotalStats.maxUsageCount
    data[GLOBAL_MIN_USAGE_COUNT_KEY] = globalTotalStats.minUsageCount
    data[GLOBAL_MAX_USAGE_COUNT_KEY + versionPattern] = updatedGlobalTotalStats.maxUsageCount
    data[GLOBAL_MIN_USAGE_COUNT_KEY + versionPattern] = updatedGlobalTotalStats.minUsageCount

    project?.let {
      if (project.isDisposed) {
        return@let
      }

      val fem = FileEditorManager.getInstance(it)
      data[OPEN_FILE_TYPES_KEY] = fem.openFiles.map { file -> file.fileType.name }.distinct()
      data[NUMBER_OF_OPEN_EDITORS_KEY] = fem.allEditors.size
      data[IS_SINGLE_MODULE_PROJECT] = ModuleManager.getInstance(it).modules.size == 1
    }

    return data
  }
}