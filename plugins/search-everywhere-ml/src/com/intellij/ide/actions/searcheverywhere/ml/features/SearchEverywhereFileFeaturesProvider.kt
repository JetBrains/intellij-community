// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.filePrediction.features.history.FileHistoryManagerWrapper
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.favoritesTreeView.FavoritesManager
import com.intellij.internal.statistic.local.FileTypeUsageLocalSummary
import com.intellij.internal.statistic.local.FileTypeUsageSummary
import com.intellij.internal.statistic.local.FileTypeUsageSummaryProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.Time.*

internal class SearchEverywhereFileFeaturesProvider : SearchEverywhereElementFeaturesProvider() {
  companion object {
    internal const val IS_DIRECTORY_DATA_KEY = "isDirectory"
    internal const val FILETYPE_DATA_KEY = "fileType"
    internal const val IS_FAVORITE_DATA_KEY = "isFavorite"
    internal const val IS_OPENED_DATA_KEY = "isOpened"
    internal const val RECENT_INDEX_DATA_KEY = "recentFilesIndex"
    internal const val PREDICTION_SCORE_DATA_KEY = "predictionScore"
    internal const val PRIORITY_DATA_KEY = "priority"
    internal const val IS_SAME_MODULE_DATA_KEY = "isSameModule"
    internal const val PACKAGE_DISTANCE_DATA_KEY = "packageDistance"

    internal const val FILETYPE_USAGE_RATIO_DATA_KEY = "fileTypeUsageRatio"
    internal const val TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY = "timeSinceLastFileTypeUsage"
    internal const val FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY = "fileTypeUsedInLastMinute"
    internal const val FILETYPE_USED_IN_LAST_HOUR_DATA_KEY = "fileTypeUsedInLastHour"
    internal const val FILETYPE_USED_IN_LAST_DAY_DATA_KEY = "fileTypeUsedInLastDay"
    internal const val FILETYPE_USED_IN_LAST_MONTH_DATA_KEY = "fileTypeUsedInLastMonth"

    internal const val TIME_SINCE_LAST_MODIFICATION_DATA_KEY = "timeSinceLastModification"
    internal const val WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY = "wasModifiedInLastMinute"
    internal const val WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY = "wasModifiedInLastHour"
    internal const val WAS_MODIFIED_IN_LAST_DAY_DATA_KEY = "wasModifiedInLastDay"
    internal const val WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY = "wasModifiedInLastMonth"
  }

  override fun getDataToCache(project: Project?): Any? {
    if (project == null) {
      return null
    }

    val openedFile = FileEditorManager.getInstance(project).selectedEditor?.file
    return Cache(deepCopyFileTypeStats(project), openedFile)
  }

  /**
   * Creates a deep copy of the file type stats obtained from the [FileTypeUsageLocalSummary],
   * so they can be safely used without running into an issue whereupon search
   * result selection, the stats get updated before calculating the file features
   * resulting in a negative timeSinceLastFileTypeUsage.
   */
  private fun deepCopyFileTypeStats(project: Project): Map<String, FileTypeUsageSummary> {
    val service = project.service<FileTypeUsageSummaryProvider>()
    val statsCopy = service.getFileTypeStats().mapValues {
      FileTypeUsageSummary(it.value.usageCount, it.value.lastUsed)
    }

    return statsCopy
  }

  override fun isElementSupported(element: Any): Boolean {
    return when (element) {
      is PsiFileSystemItem -> element.virtualFile != null
      is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> isElementSupported(element.item)
      else -> false
    }
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  queryLength: Int,
                                  elementPriority: Int,
                                  cache: Any?): Map<String, Any> {
    val item = when (element) {
      is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> element.item as PsiFileSystemItem
      is PsiFileSystemItem -> element
      else -> return emptyMap()
    }

    val fileTypeStats = cache as Cache
    return getFeatures(item, currentTime, elementPriority, fileTypeStats)
  }

  private fun getFeatures(item: PsiFileSystemItem,
                          currentTime: Long,
                          elementPriority: Int,
                          cache: Cache): Map<String, Any> {
    val data = hashMapOf<String, Any>(
      IS_FAVORITE_DATA_KEY to isFavorite(item),
      IS_DIRECTORY_DATA_KEY to item.isDirectory,
      PRIORITY_DATA_KEY to elementPriority,
    )

    if (item.isDirectory) {
      // Rest of the features are only applicable to files, not directories
      return data
    }

    data[IS_OPENED_DATA_KEY] = isOpened(item)
    data[FILETYPE_DATA_KEY] = item.virtualFile.fileType.name
    data[RECENT_INDEX_DATA_KEY] = getRecentFilesIndex(item)
    data[PREDICTION_SCORE_DATA_KEY] = getPredictionScore(item)
    data[IS_SAME_MODULE_DATA_KEY] = isSameModuleAsOpenedFile(item, cache.openedFile)
    data[PACKAGE_DISTANCE_DATA_KEY] = calculatePackageDistance(item, cache.openedFile)

    data.putAll(getModificationTimeStats(item, currentTime))
    data.putAll(getFileTypeStats(item, currentTime, cache.fileTypeStats))

    return data
  }

  private fun isFavorite(item: PsiFileSystemItem): Boolean {
    val favoritesManager = FavoritesManager.getInstance(item.project)
    return ReadAction.compute<Boolean, Nothing> { favoritesManager.getFavoriteListName(null, item.virtualFile) != null }
  }

  private fun isOpened(item: PsiFileSystemItem): Boolean {
    val openedFiles = FileEditorManager.getInstance(item.project).openFiles
    return item.virtualFile in openedFiles
  }

  private fun getRecentFilesIndex(item: PsiFileSystemItem): Int {
    val historyManager = EditorHistoryManager.getInstance(item.project)
    val recentFilesList = historyManager.fileList

    val fileIndex = recentFilesList.indexOf(item.virtualFile)
    if (fileIndex == -1) {
      return fileIndex
    }

    // Give the most recent files the lowest index value
    return recentFilesList.size - fileIndex
  }

  private fun getModificationTimeStats(item: PsiFileSystemItem, currentTime: Long): Map<String, Any> {
    val timeSinceLastMod = currentTime - item.virtualFile.timeStamp

    return hashMapOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to timeSinceLastMod,
      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY to (timeSinceLastMod <= MINUTE),
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY to (timeSinceLastMod <= HOUR),
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY to (timeSinceLastMod <= DAY),
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY to (timeSinceLastMod <= (4 * WEEK.toLong()))
    )
  }

  private fun getFileTypeStats(item: PsiFileSystemItem,
                               currentTime: Long,
                               fileTypeStats: Map<String, FileTypeUsageSummary>): Map<String, Any> {
    val totalUsage = fileTypeStats.values.sumBy { it.usageCount }
    val stats = fileTypeStats[item.virtualFile.fileType.name]

    val timeSinceLastUsage = if (stats == null) Long.MAX_VALUE else currentTime - stats.lastUsed
    val usageRatio = if (stats == null) 0.0 else roundDouble(stats.usageCount.toDouble() / totalUsage)

    return hashMapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to usageRatio,

      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY to timeSinceLastUsage,
      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY to (timeSinceLastUsage <= MINUTE),
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY to (timeSinceLastUsage <= HOUR),
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY to (timeSinceLastUsage <= DAY),
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY to (timeSinceLastUsage <= (4 * WEEK.toLong()))
    )
  }

  private fun getPredictionScore(item: PsiFileSystemItem): Double {
    val historyManagerWrapper = FileHistoryManagerWrapper.getInstance(item.project)
    val probability = historyManagerWrapper.calcNextFileProbability(item.virtualFile)
    return roundDouble(probability)
  }

  private fun isSameModuleAsOpenedFile(item: PsiFileSystemItem, openedFile: VirtualFile?): Boolean {
    if (openedFile == null) {
      return false
    }

    val project = item.project

    val fileIndex = ReadAction.compute<ProjectFileIndex, Nothing> { ProjectRootManager.getInstance(project).fileIndex }
    val openedFileModule = fileIndex.getModuleForFile(openedFile) ?: return false
    val itemModule = fileIndex.getModuleForFile(item.virtualFile) ?: return false

    return openedFileModule == itemModule
  }

  /**
   * Calculates the package distance of the found [item] relative to the [openedFile].
   *
   * The distance can be considered the number of steps/changes to reach the other package,
   * for instance the distance to a parent or a child of a package is equal to 1,
   * and the distance from package a.b.c.d to package a.b.x.y is equal to 4.
   */
  private fun calculatePackageDistance(item: PsiFileSystemItem, openedFile: VirtualFile?): Int {
    if (openedFile == null) {
      return -1
    }

    val project = item.project

    val fileIndex = ReadAction.compute<ProjectFileIndex, Nothing> { ProjectRootManager.getInstance(project).fileIndex }

    val openedFilePackage = fileIndex.getPackageNameByDirectory(openedFile.parent)?.split('.')
    val foundFilePackage = fileIndex.getPackageNameByDirectory(item.virtualFile.parent)?.split('.')

    if (openedFilePackage == null || foundFilePackage == null) {
      return -1
    }

    var distance = 0
    for ((index, value) in openedFilePackage.withIndex()) {
      if (foundFilePackage.size == index) {
        // found file package is a parent of the opened file's package
        distance = openedFilePackage.size - foundFilePackage.size
        break
      }

      if (foundFilePackage[index] == value) {
        // As long as these are the same, the distance remains 0
        continue
      }
      else {
        distance = (foundFilePackage.size - index) + (openedFilePackage.size - index)
        break
      }
    }

    // Check if the found file package is a child of the opened file package
    if (distance == 0 && foundFilePackage.size > openedFilePackage.size) {
      distance = foundFilePackage.size - openedFilePackage.size
    }

    return distance
  }

  private data class Cache(val fileTypeStats: Map<String, FileTypeUsageSummary>, val openedFile: VirtualFile?)
}