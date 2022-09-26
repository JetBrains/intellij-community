// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.ml.yaml.features

import com.intellij.ide.actions.searcheverywhere.ml.features.FeaturesProviderCache
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereElementFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.statistician.SearchEverywhereStatisticianService
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Time
import org.jetbrains.yaml.navigation.YAMLKeyNavigationItem
import org.jetbrains.yaml.navigation.YAMLKeysSearchEverywhereContributor

private class SEYamlKeyFeaturesProvider : SearchEverywhereElementFeaturesProvider(YAMLKeysSearchEverywhereContributor::class.java) {
  companion object {
    private val KEY_IS_MOST_RECENTLY_USED = EventFields.Boolean("yamlKeyIsMostRecentlyUsed")
    private val KEY_IS_IN_TOP_5_RECENTLY_USED = EventFields.Boolean("yamlKeyIsInTop5RecentlyUsed")
    private val KEY_NEVER_USED = EventFields.Boolean("yamlKeyNeverUsed")
    private val KEY_IS_MOST_POPULAR = EventFields.Boolean("yamlKeyIsMostPopular")

    private val FILE_RECENCY_INDEX = EventFields.Int("yamlFileRecencyIndex")
    private val FILE_TIME_SINCE_LAST_MODIFICATION = EventFields.Long("yamlTimeSinceLastModification")
    private val FILE_MODIFIED_IN_LAST_HOUR = EventFields.Boolean("yamlFileModifiedInLastHour")
    private val FILE_MODIFIED_IN_LAST_DAY = EventFields.Boolean("yamlFileModifiedInLastDay")
    private val FILE_MODIFIED_IN_LAST_WEEK = EventFields.Boolean("yamlFileModifiedInLastWeek")
    private val FILE_MODIFIED_IN_LAST_MONTH = EventFields.Boolean("yamlFileModifiedInLastMonth")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return listOf(
      KEY_IS_MOST_RECENTLY_USED, KEY_IS_IN_TOP_5_RECENTLY_USED,
      KEY_NEVER_USED, KEY_IS_MOST_POPULAR,
      FILE_RECENCY_INDEX, FILE_TIME_SINCE_LAST_MODIFICATION,
      FILE_MODIFIED_IN_LAST_HOUR, FILE_MODIFIED_IN_LAST_DAY,
      FILE_MODIFIED_IN_LAST_WEEK, FILE_MODIFIED_IN_LAST_MONTH,
    )
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    if (element !is YAMLKeyNavigationItem) return emptyList()
    return getKeyNameMatchingFeatures(element, searchQuery) + getStatisticianFeatures(element) + getFileFeatures(element, currentTime)
  }

  private fun getKeyNameMatchingFeatures(element: NavigationItem, searchQuery: String): Collection<EventPair<*>> {
    val name = element.name ?: return emptyList()
    return getNameMatchingFeatures(name, searchQuery)
  }

  private fun getStatisticianFeatures(element: YAMLKeyNavigationItem): Collection<EventPair<*>> {
    val stats = service<SearchEverywhereStatisticianService>().getCombinedStats(element) ?: return emptyList()

    return listOf(
      KEY_IS_MOST_RECENTLY_USED.with(stats.isMostRecent),
      KEY_IS_IN_TOP_5_RECENTLY_USED.with(stats.recency < 5),
      KEY_NEVER_USED.with(stats.recency == Int.MAX_VALUE),
      KEY_IS_MOST_POPULAR.with(stats.isMostPopular),
    )
  }

  private fun getFileFeatures(element: YAMLKeyNavigationItem, currentTime: Long): Collection<EventPair<*>> {
    val timeSinceLastModification = currentTime - element.file.timeStamp

    return listOf(
      FILE_RECENCY_INDEX.with(getRecentFilesIndex(element.project, element.file)),
      FILE_TIME_SINCE_LAST_MODIFICATION.with(timeSinceLastModification),
      FILE_MODIFIED_IN_LAST_HOUR.with (timeSinceLastModification <= Time.HOUR),
      FILE_MODIFIED_IN_LAST_DAY.with(timeSinceLastModification <= Time.DAY),
      FILE_MODIFIED_IN_LAST_WEEK.with(timeSinceLastModification <= Time.WEEK),
      FILE_MODIFIED_IN_LAST_MONTH.with(timeSinceLastModification <= Time.WEEK * 4L),
    )
  }

  private fun getRecentFilesIndex(project: Project, file: VirtualFile): Int {
    val historyManager = EditorHistoryManager.getInstance(project)
    val recentFilesList = historyManager.fileList

    val fileIndex = recentFilesList.indexOf(file)
    if (fileIndex == -1) {
      return fileIndex
    }

    // Give the most recent files the lowest index value
    return recentFilesList.size - fileIndex
  }
}
