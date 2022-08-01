// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.filePrediction.features.history.FileHistoryManagerWrapper
import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.RecentFilesSEContributor
import com.intellij.ide.bookmarks.BookmarkManager
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.PathUtil
import com.intellij.util.Time.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@IntellijInternalApi
class SearchEverywhereFileFeaturesProvider
  : SearchEverywhereElementFeaturesProvider(FileSearchEverywhereContributor::class.java, RecentFilesSEContributor::class.java) {
  companion object {
    val FILETYPE_DATA_KEY = EventFields.StringValidatedByCustomRule("fileType", "file_type")
    val IS_BOOKMARK_DATA_KEY = EventFields.Boolean("isBookmark")
    val IS_OPENED_DATA_KEY = EventFields.Boolean("isOpened")

    internal val IS_DIRECTORY_DATA_KEY = EventFields.Boolean("isDirectory")
    internal val RECENT_INDEX_DATA_KEY = EventFields.Int("recentFilesIndex")
    internal val PREDICTION_SCORE_DATA_KEY = EventFields.Double("predictionScore")
    internal val IS_EXACT_MATCH_DATA_KEY = EventFields.Boolean("isExactMatch")
    internal val FILETYPE_MATCHES_QUERY_DATA_KEY = EventFields.Boolean("fileTypeMatchesQuery")
    internal val IS_TOP_LEVEL_DATA_KEY = EventFields.Boolean("isTopLevel")

    internal val TIME_SINCE_LAST_MODIFICATION_DATA_KEY = EventFields.Long("timeSinceLastModification")
    internal val WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY = EventFields.Boolean("wasModifiedInLastMinute")
    internal val WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY = EventFields.Boolean("wasModifiedInLastHour")
    internal val WAS_MODIFIED_IN_LAST_DAY_DATA_KEY = EventFields.Boolean("wasModifiedInLastDay")
    internal val WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY = EventFields.Boolean("wasModifiedInLastMonth")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return arrayListOf<EventField<*>>(
      IS_DIRECTORY_DATA_KEY, FILETYPE_DATA_KEY, IS_BOOKMARK_DATA_KEY, IS_OPENED_DATA_KEY, RECENT_INDEX_DATA_KEY,
      PREDICTION_SCORE_DATA_KEY, IS_EXACT_MATCH_DATA_KEY, FILETYPE_MATCHES_QUERY_DATA_KEY,
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY, WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY, WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY,
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY, WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY, IS_TOP_LEVEL_DATA_KEY
    )
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    val item = (SearchEverywherePsiElementFeaturesProvider.getPsiElement(element) as? PsiFileSystemItem) ?: return emptyList()

    val data = arrayListOf<EventPair<*>>(
      IS_BOOKMARK_DATA_KEY.with(isBookmark(item)),
      IS_DIRECTORY_DATA_KEY.with(item.isDirectory),
      IS_EXACT_MATCH_DATA_KEY.with(elementPriority == GotoFileItemProvider.EXACT_MATCH_DEGREE)
    )

    data.putIfValueNotNull(IS_TOP_LEVEL_DATA_KEY, isTopLevel(item))

    val nameOfItem = item.virtualFile.nameWithoutExtension
    // Remove the directory and the extension if they are present
    val fileNameFromQuery = FileUtil.getNameWithoutExtension(PathUtil.getFileName(searchQuery))
    data.addAll(getNameMatchingFeatures(nameOfItem, fileNameFromQuery))

    if (item.isDirectory) {
      // Rest of the features are only applicable to files, not directories
      return data
    }

    data.add(IS_OPENED_DATA_KEY.with(isOpened(item)))
    data.add(FILETYPE_DATA_KEY.with(item.virtualFile.fileType.name))
    data.putIfValueNotNull(FILETYPE_MATCHES_QUERY_DATA_KEY, matchesFileTypeInQuery(item, searchQuery))
    data.add(RECENT_INDEX_DATA_KEY.with(getRecentFilesIndex(item)))
    data.add(PREDICTION_SCORE_DATA_KEY.with(getPredictionScore(item)))

    data.addAll(getModificationTimeStats(item, currentTime))
    return data
  }

  private fun isBookmark(item: PsiFileSystemItem): Boolean {
    val bookmarkManager = BookmarkManager.getInstance(item.project)
    return ReadAction.compute<Boolean, Nothing> { item.virtualFile?.let { bookmarkManager.findFileBookmark(it) } != null }
  }

  private fun isTopLevel(item: PsiFileSystemItem): Boolean? {
    val basePath = item.project.guessProjectDir()?.path ?: return null
    val fileDirectoryPath = item.virtualFile.parent?.path ?: return null

    return fileDirectoryPath == basePath
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

  private fun getModificationTimeStats(item: PsiFileSystemItem, currentTime: Long): List<EventPair<*>> {
    val timeSinceLastMod = currentTime - item.virtualFile.timeStamp

    return arrayListOf<EventPair<*>>(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY.with(timeSinceLastMod),
      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY.with((timeSinceLastMod <= MINUTE)),
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY.with((timeSinceLastMod <= HOUR)),
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY.with((timeSinceLastMod <= DAY)),
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY.with((timeSinceLastMod <= (4 * WEEK.toLong())))
    )
  }

  private fun getPredictionScore(item: PsiFileSystemItem): Double {
    val historyManagerWrapper = FileHistoryManagerWrapper.getInstance(item.project)
    val probability = historyManagerWrapper.calcNextFileProbability(item.virtualFile)
    return roundDouble(probability)
  }
}
