// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.filePrediction.features.history.FileHistoryManagerWrapper
import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.ide.favoritesTreeView.FavoritesManager
import com.intellij.internal.statistic.local.FileTypeUsageLocalSummary
import com.intellij.internal.statistic.local.FileTypeUsageSummary
import com.intellij.internal.statistic.local.FileTypeUsageSummaryProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.textMatching.PrefixMatchingUtil
import com.intellij.util.PathUtil
import com.intellij.util.Time.*

internal class SearchEverywhereFileFeaturesProvider : SearchEverywhereElementFeaturesProvider(FileSearchEverywhereContributor::class.java) {
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
    internal const val PACKAGE_DISTANCE_NORMALIZED_DATA_KEY = "packageDistanceNorm"
    internal const val IS_SAME_FILETYPE_AS_OPENED_FILE_DATA_KEY = "isSameFileTypeAsOpenedFile"
    internal const val IS_EXACT_MATCH_DATA_KEY = "isExactMatch"

    internal const val IS_IN_SOURCE_DATA_KEY = "isInSource"
    internal const val IS_IN_TEST_SOURCES_DATA_KEY = "isInTestSources"
    internal const val IS_IN_LIBRARY_DATA_KEY = "isFromLibrary"
    internal const val IS_EXCLUDED_DATA_KEY = "isInExcluded"

    internal const val FILETYPE_MATCHES_QUERY_DATA_KEY = "fileTypeMatchesQuery"
    internal const val FILETYPE_USAGE_RATIO_DATA_KEY = "fileTypeUsageRatio"
    internal const val FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY = "fileTypeUsageRatioToMax"
    internal const val FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY = "fileTypeUsageRatioToMin"
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

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: Any?): Map<String, Any> {
    val item = when (element) {
      is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> element.item as PsiFileSystemItem
      is PsiFileSystemItem -> element
      else -> return emptyMap()
    }

    val fileTypeStats = cache as Cache
    return getFeatures(item, currentTime, searchQuery, elementPriority, fileTypeStats)
  }

  private fun getFeatures(item: PsiFileSystemItem,
                          currentTime: Long,
                          searchQuery: String,
                          elementPriority: Int,
                          cache: Cache): Map<String, Any> {
    val data = hashMapOf<String, Any>(
      IS_FAVORITE_DATA_KEY to isFavorite(item),
      IS_DIRECTORY_DATA_KEY to item.isDirectory,
      PRIORITY_DATA_KEY to elementPriority,
      IS_EXACT_MATCH_DATA_KEY to (elementPriority == GotoFileItemProvider.EXACT_MATCH_DEGREE),
      SearchEverywhereUsageTriggerCollector.TOTAL_SYMBOLS_AMOUNT_DATA_KEY to searchQuery.length,
    )

    data.putAll(getNameMatchingFeatures(item, searchQuery))
    data.putAll(getFileLocationStats(item))

    calculatePackageDistance(item, cache.openedFile)?.let {
      data[PACKAGE_DISTANCE_DATA_KEY] = it.first
      data[PACKAGE_DISTANCE_NORMALIZED_DATA_KEY] = it.second
    }

    data.putIfValueNotNull(IS_SAME_MODULE_DATA_KEY, isSameModuleAsOpenedFile(item, cache.openedFile))

    if (item.isDirectory) {
      // Rest of the features are only applicable to files, not directories
      return data
    }

    data[IS_OPENED_DATA_KEY] = isOpened(item)
    data[FILETYPE_DATA_KEY] = item.virtualFile.fileType.name
    data.putIfValueNotNull(FILETYPE_MATCHES_QUERY_DATA_KEY, matchesFileTypeInQuery(item, searchQuery))
    data[RECENT_INDEX_DATA_KEY] = getRecentFilesIndex(item)
    data[PREDICTION_SCORE_DATA_KEY] = getPredictionScore(item)
    data.putIfValueNotNull(IS_SAME_FILETYPE_AS_OPENED_FILE_DATA_KEY, isSameFileTypeAsOpenedFile(item, cache.openedFile))

    data.putAll(getModificationTimeStats(item, currentTime))
    data.putAll(getFileTypeStats(item, currentTime, cache.fileTypeStats))

    return data
  }

  private fun isFavorite(item: PsiFileSystemItem): Boolean {
    val favoritesManager = FavoritesManager.getInstance(item.project)
    return ReadAction.compute<Boolean, Nothing> { favoritesManager.getFavoriteListName(null, item.virtualFile) != null }
  }

  private fun getNameMatchingFeatures(item: PsiFileSystemItem, searchQuery: String): Map<String, Any> {
    fun changeToCamelCase(str: String): String {
      val words = str.split('_')
      val firstWord = words.first()
      if (words.size == 1) {
        return firstWord
      } else {
        return firstWord.plus(
          words.subList(1, words.size)
            .joinToString(separator = "") { s -> s.replaceFirstChar { it.uppercaseChar() } }
        )
      }
    }

    // Remove the directory and the extension if they are present
    val filename = FileUtil.getNameWithoutExtension(PathUtil.getFileName(searchQuery))

    val features = mutableMapOf<String, Any>()
    PrefixMatchingUtil.calculateFeatures(item.virtualFile.nameWithoutExtension, filename, features)
    return features.mapKeys { changeToCamelCase(it.key) }  // Change snake case to camel case for consistency with other feature names.
      .mapValues { if (it.value is Double) roundDouble(it.value as Double) else it.value }
  }

  private fun isOpened(item: PsiFileSystemItem): Boolean {
    val openedFiles = FileEditorManager.getInstance(item.project).openFiles
    return item.virtualFile in openedFiles
  }

  private fun matchesFileTypeInQuery(item: PsiFileSystemItem, searchQuery: String): Boolean? {
    val fileExtension = item.virtualFile.extension
    val extensionInQuery = searchQuery.substringAfterLast('.', missingDelimiterValue = "")
    if (extensionInQuery.isEmpty() || fileExtension == null) {
      return null
    }

    return extensionInQuery == fileExtension
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
    val totalUsage = fileTypeStats.values.sumOf { it.usageCount }
    val stats = fileTypeStats[item.virtualFile.fileType.name]

    if (stats == null) {
      return emptyMap()
    }

    val timeSinceLastUsage = currentTime - stats.lastUsed
    val usageRatio = roundDouble(stats.usageCount.toDouble() / totalUsage)
    val min = fileTypeStats.minOf { it.value.usageCount }
    val max = fileTypeStats.maxOf { it.value.usageCount }

    return hashMapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to usageRatio,
      FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY to roundDouble(stats.usageCount.toDouble() / max),
      FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY to roundDouble(stats.usageCount.toDouble() / min),

      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY to timeSinceLastUsage,
      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY to (timeSinceLastUsage <= MINUTE),
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY to (timeSinceLastUsage <= HOUR),
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY to (timeSinceLastUsage <= DAY),
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY to (timeSinceLastUsage <= (4 * WEEK.toLong()))
    )
  }

  private fun isSameFileTypeAsOpenedFile(item: PsiFileSystemItem, openedFile: VirtualFile?): Boolean? {
    val openedFileType = openedFile?.fileType ?: return null
    return FileTypeRegistry.getInstance().isFileOfType(item.virtualFile, openedFileType)
  }

  private fun getPredictionScore(item: PsiFileSystemItem): Double {
    val historyManagerWrapper = FileHistoryManagerWrapper.getInstance(item.project)
    val probability = historyManagerWrapper.calcNextFileProbability(item.virtualFile)
    return roundDouble(probability)
  }

  private fun isSameModuleAsOpenedFile(item: PsiFileSystemItem, openedFile: VirtualFile?): Boolean? {
    if (openedFile == null) {
      return null
    }

    val project = item.project

    val (openedFileModule, itemModule) = ReadAction.compute<Pair<Module?, Module?>, Nothing> {
      val fileIndex = ProjectRootManager.getInstance(project).fileIndex
      Pair(fileIndex.getModuleForFile(openedFile), fileIndex.getModuleForFile(item.virtualFile))
    }

    if (openedFileModule == null || itemModule == null) {
      return null
    }

    return openedFileModule == itemModule
  }

  /**
   * Calculates the package distance of the found [item] relative to the [openedFile].
   *
   * The distance can be considered the number of steps/changes to reach the other package,
   * for instance the distance to a parent or a child of a package is equal to 1,
   * and the distance from package a.b.c.d to package a.b.x.y is equal to 4.
   *
   * @return Pair of distance and normalized distance, or null if it could not be calculated.
   */
  private fun calculatePackageDistance(item: PsiFileSystemItem, openedFile: VirtualFile?): Pair<Int, Double>? {
    if (openedFile == null) {
      return null
    }

    val project = item.project

    val (openedFilePackage, foundFilePackage) = ReadAction.compute<Pair<String?, String?>, Nothing> {
      val fileIndex = ProjectRootManager.getInstance(project).fileIndex

      val openedFilePackageName = getVirtualFileDirectory(openedFile)?.let { fileIndex.getPackageNameByDirectory(it) }
      val foundFilePackageName = getVirtualFileDirectory(item.virtualFile)?.let { fileIndex.getPackageNameByDirectory(it) }

      Pair(openedFilePackageName, foundFilePackageName)
    }.run {
      fun splitPackage(s: String?) = if (s == null) {
        null
      }
      else if (s.isBlank()) {
        // In case the file is under a source root (src/testSrc/resource) and the package prefix is blank
        emptyList()
      }
      else {
        s.split('.')
      }

      Pair(splitPackage(first), splitPackage(second))
    }

    if (openedFilePackage == null || foundFilePackage == null) {
      return null
    }

    val maxDistance = openedFilePackage.size + foundFilePackage.size
    var common = 0
    for ((index, value) in openedFilePackage.withIndex()) {
      if (foundFilePackage.size == index || foundFilePackage[index] != value) {
        // Stop counting if the found file package is a parent or it no longer matches the opened file package
        break
      }

      common++
    }

    val distance = maxDistance - 2 * common
    val normalizedDistance = roundDouble(if (maxDistance != 0) (distance.toDouble() / maxDistance) else 0.0)
    return Pair(distance, normalizedDistance)
  }

  private fun getFileLocationStats(file: PsiFileSystemItem): Map<String, Any> {
    val project = file.project
    val virtualFile = file.virtualFile

    return ReadAction.compute<Map<String, Any>, Nothing> {
      val fileIndex = ProjectFileIndex.getInstance(project)

      return@compute mapOf(
        IS_IN_SOURCE_DATA_KEY to fileIndex.isInSource(virtualFile),
        IS_IN_TEST_SOURCES_DATA_KEY to fileIndex.isInTestSourceContent(virtualFile),
        IS_IN_LIBRARY_DATA_KEY to fileIndex.isInLibrary(virtualFile),
        IS_EXCLUDED_DATA_KEY to fileIndex.isExcluded(virtualFile),
      )
    }
  }

  private fun getVirtualFileDirectory(file: VirtualFile, maxChecksUp: Int = 3): VirtualFile? {
    if (file.isDirectory) {
      return file
    }

    if (maxChecksUp > 1) {
      val parent = file.parent ?: return null
      return getVirtualFileDirectory(parent, maxChecksUp - 1)
    }
    else {
      return null
    }
  }

  private data class Cache(val fileTypeStats: Map<String, FileTypeUsageSummary>, val openedFile: VirtualFile?)

  /**
   * Associates the specified key with the value, only if the value is not null.
   */
  private fun <K, V> MutableMap<K, V>.putIfValueNotNull(key: K, value: V?) {
    value?.let {
      this[key] = it
    }
  }
}
