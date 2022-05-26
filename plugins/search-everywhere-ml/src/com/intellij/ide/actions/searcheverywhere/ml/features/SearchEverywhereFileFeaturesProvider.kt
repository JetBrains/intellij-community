// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.filePrediction.features.history.FileHistoryManagerWrapper
import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.RecentFilesSEContributor
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.ide.favoritesTreeView.FavoritesManager
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.PathUtil
import com.intellij.util.Time.*

internal class SearchEverywhereFileFeaturesProvider
  : SearchEverywhereClassOrFileFeaturesProvider(FileSearchEverywhereContributor::class.java, RecentFilesSEContributor::class.java) {
  companion object {
    internal const val IS_DIRECTORY_DATA_KEY = "isDirectory"
    internal const val FILETYPE_DATA_KEY = "fileType"
    internal const val IS_FAVORITE_DATA_KEY = "isFavorite"
    internal const val IS_OPENED_DATA_KEY = "isOpened"
    internal const val RECENT_INDEX_DATA_KEY = "recentFilesIndex"
    internal const val PREDICTION_SCORE_DATA_KEY = "predictionScore"
    internal const val PRIORITY_DATA_KEY = "priority"
    internal const val IS_EXACT_MATCH_DATA_KEY = "isExactMatch"
    internal const val FILETYPE_MATCHES_QUERY_DATA_KEY = "fileTypeMatchesQuery"

    internal const val TIME_SINCE_LAST_MODIFICATION_DATA_KEY = "timeSinceLastModification"
    internal const val WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY = "wasModifiedInLastMinute"
    internal const val WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY = "wasModifiedInLastHour"
    internal const val WAS_MODIFIED_IN_LAST_DAY_DATA_KEY = "wasModifiedInLastDay"
    internal const val WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY = "wasModifiedInLastMonth"
  }

  override fun getElementFeatures(element: PsiElement,
                                  presentation: TargetPresentation?,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: Cache?): Map<String, Any> {
    val item = (element as? PsiFileSystemItem) ?: return emptyMap()

    val data = hashMapOf<String, Any>(
      IS_FAVORITE_DATA_KEY to isFavorite(item),
      IS_DIRECTORY_DATA_KEY to item.isDirectory,
      PRIORITY_DATA_KEY to elementPriority,
      IS_EXACT_MATCH_DATA_KEY to (elementPriority == GotoFileItemProvider.EXACT_MATCH_DEGREE),
      SearchEverywhereUsageTriggerCollector.TOTAL_SYMBOLS_AMOUNT_DATA_KEY to searchQuery.length,
    )


    val nameOfItem = item.virtualFile.nameWithoutExtension
    // Remove the directory and the extension if they are present
    val fileNameFromQuery = FileUtil.getNameWithoutExtension(PathUtil.getFileName(searchQuery))
    data.putAll(getNameMatchingFeatures(nameOfItem, fileNameFromQuery))

    if (item.isDirectory) {
      // Rest of the features are only applicable to files, not directories
      return data
    }

    data[IS_OPENED_DATA_KEY] = isOpened(item)
    data[FILETYPE_DATA_KEY] = item.virtualFile.fileType.name
    data.putIfValueNotNull(FILETYPE_MATCHES_QUERY_DATA_KEY, matchesFileTypeInQuery(item, searchQuery))
    data[RECENT_INDEX_DATA_KEY] = getRecentFilesIndex(item)
    data[PREDICTION_SCORE_DATA_KEY] = getPredictionScore(item)

    data.putAll(getModificationTimeStats(item, currentTime))
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

  private fun getPredictionScore(item: PsiFileSystemItem): Double {
    val historyManagerWrapper = FileHistoryManagerWrapper.getInstance(item.project)
    val probability = historyManagerWrapper.calcNextFileProbability(item.virtualFile)
    return roundDouble(probability)
  }
}
