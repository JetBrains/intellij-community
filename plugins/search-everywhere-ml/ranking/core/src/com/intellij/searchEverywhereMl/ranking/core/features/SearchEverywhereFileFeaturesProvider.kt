// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.RecentFilesSEContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.FileBookmark
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsagesCollector
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereFileFeaturesProvider.Fields.FILETYPE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereFileFeaturesProvider.Fields.FILETYPE_MATCHES_QUERY_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereFileFeaturesProvider.Fields.IS_BOOKMARK_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereFileFeaturesProvider.Fields.IS_DIRECTORY_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereFileFeaturesProvider.Fields.IS_EXACT_MATCH_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereFileFeaturesProvider.Fields.IS_EXACT_MATCH_WITH_REL_PATH_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereFileFeaturesProvider.Fields.IS_TOP_LEVEL_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereFileFeaturesProvider.Fields.REL_PATH_NAME_FEATURE_TO_FIELD
import com.intellij.textMatching.PrefixMatchingUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.InvalidPathException
import java.nio.file.Path

@ApiStatus.Internal
class SearchEverywhereFileFeaturesProvider
  : SearchEverywhereElementFeaturesProvider(FileSearchEverywhereContributor::class.java, RecentFilesSEContributor::class.java) {

  object Fields {
    val FILETYPE_DATA_KEY = EventFields.StringValidatedByCustomRule("file_type", FileTypeUsagesCollector.ValidationRule::class.java)
    val IS_BOOKMARK_DATA_KEY = EventFields.Boolean("is_bookmark")

    val IS_DIRECTORY_DATA_KEY = EventFields.Boolean("is_directory")
    val IS_EXACT_MATCH_DATA_KEY = EventFields.Boolean("is_exact_match")
    val FILETYPE_MATCHES_QUERY_DATA_KEY = EventFields.Boolean("file_type_matches_query")
    val IS_TOP_LEVEL_DATA_KEY = EventFields.Boolean("is_top_level")
    val IS_EXACT_MATCH_WITH_REL_PATH_DATA_KEY = EventFields.Boolean("is_exact_relative_path")

    val REL_PATH_NAME_FEATURE_TO_FIELD = hashMapOf<String, EventField<*>>(
      "prefix_same_start_count" to EventFields.Int("rel_path_prefix_same_start_count"),
      "prefix_greedy_score" to EventFields.Double("rel_path_prefix_greedy_score"),
      "prefix_matched_words_score" to EventFields.Double("rel_path_prefix_matched_words_score"),
      "prefix_matched_words_relative" to EventFields.Double("rel_path_prefix_matched_words_relative")
    )
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    return listOf(
      IS_DIRECTORY_DATA_KEY, FILETYPE_DATA_KEY, IS_BOOKMARK_DATA_KEY,
      IS_EXACT_MATCH_DATA_KEY, FILETYPE_MATCHES_QUERY_DATA_KEY, IS_TOP_LEVEL_DATA_KEY,
      IS_EXACT_MATCH_WITH_REL_PATH_DATA_KEY
    ) + REL_PATH_NAME_FEATURE_TO_FIELD.values
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?,
                                  correction: SearchEverywhereSpellCheckResult): List<EventPair<*>> {
    val item = (SearchEverywherePsiElementFeaturesProviderUtils.getPsiElementOrNull(element) as? PsiFileSystemItem) ?: return emptyList()

    return buildList {
      add(IS_BOOKMARK_DATA_KEY.with(isBookmark(item)))
      add(IS_DIRECTORY_DATA_KEY.with(item.isDirectory))
      add(IS_EXACT_MATCH_DATA_KEY.with(isExactMatch(item, searchQuery, elementPriority)))
      add(IS_EXACT_MATCH_WITH_REL_PATH_DATA_KEY.with(isExactRelativePath(item, searchQuery)))

      addAll(getRelativePathNameMatchingFeatures(item, searchQuery))

      putIfValueNotNull(IS_TOP_LEVEL_DATA_KEY, isTopLevel(item))

      if (item.isDirectory) {
        // Rest of the features are only applicable to files, not directories
        return@buildList
      }

      add(FILETYPE_DATA_KEY.with(item.virtualFile.fileType.name))
      putIfValueNotNull(FILETYPE_MATCHES_QUERY_DATA_KEY, matchesFileTypeInQuery(item, searchQuery))
    }
  }

  private fun isBookmark(item: PsiFileSystemItem): Boolean {
    val bookmarksManager = BookmarksManager.getInstance(item.project)
    val bkm = ReadAction.compute<Boolean, Nothing> { item.virtualFile?.let { vFile -> bookmarksManager?.bookmarks?.find { it is FileBookmark && it.file == vFile } } != null }
    return bkm
  }

  private fun isExactMatch(item: PsiFileSystemItem, searchQuery: String, elementPriority: Int): Boolean {
    /*
    The exact match feature is based on the same logic that is used in GotoFileItemProvider to add the
    exact match degree on top of the matching score. Note that even though the score gets added on top
    of the matching degree, the priority can be lower than EXACT_MATCH_DEGREE, but we can still have
    an exact match. This is because the score also contains a gap penalty, which can lower the final priority.

    There are two cases where an exact match can occur:
      1. Search query is an absolute path. In that case, there will be just one element in the results
         which will be the matching file (given, of course, that it exists).
         In that case, the priority is always exactly equal to EXACT_MATCH_DEGREE.

      2. Search query contains at least the last character of the parent directory, along with the complete
         filename and extension.
         For example, if a file "foo" with extension "ext" exists in a directory called "dir", then
         'r/foo.ext' - is an exact match
         'dir/foo.ext' - is an exact match
         but
         '/foo.ext' and 'foo.ext' are not
    */
    if (elementPriority == GotoFileItemProvider.EXACT_MATCH_DEGREE) return true  // Absolute path

    val filePath = item.virtualFile.path
    val fileName = item.virtualFile.name

    return searchQuery.asSequence()
      .map { if (it == '\\') '/' else it }
      .filterIndexed { searchQueryCharIndex, c ->
        // check if query starts with slash and contains just the filename (i.e. "/foo.ext")
        // by comparing query length and filename length we can check we should expect any more slashes in the query
        if ((searchQueryCharIndex == 0 && c == '/') && (searchQuery.length - 1) == fileName.length) return@filterIndexed true

        val index = (filePath.length - searchQuery.length + searchQueryCharIndex).takeIf { it >= 0 } ?: return@filterIndexed true
        filePath[index] != c
      }.none()
  }

  private fun isExactRelativePath(item: PsiFileSystemItem, searchQuery: String): Boolean {
    val filePath = item.virtualFile.toNioPathOrNull() ?: return false
    val basePath = item.project.guessProjectDir()?.toNioPathOrNull() ?: return false
    val queryPath = searchQuery.toNioPathOrNull() ?: return false
    try {
      val relativePath = basePath.relativize(filePath)
      return queryPath == relativePath
    }
    catch (e: IllegalArgumentException) {
      return false
    }
  }

  private fun getRelativePathNameMatchingFeatures(item: PsiFileSystemItem, searchQuery: String): Collection<EventPair<*>> {
    val filePath = item.virtualFile.toNioPathOrNull() ?: return emptyList()
    val basePath = item.project.guessProjectDir()?.toNioPathOrNull() ?: return emptyList()

    val relativePath = try {
      basePath.relativize(filePath)
    }
    catch (e: IllegalArgumentException) {
      return emptyList()
    }


    return buildList {
      buildMap {
        PrefixMatchingUtil.calculateFeatures(relativePath.toString(), searchQuery, this)
      }.mapNotNullTo(this@buildList) { (featureName, value) ->
        val field = REL_PATH_NAME_FEATURE_TO_FIELD[featureName] ?: return@mapNotNullTo null
        field.tryWith(value)
      }
    }
  }

  private fun isTopLevel(item: PsiFileSystemItem): Boolean? {
    val basePath = item.project.guessProjectDir()?.path ?: return null
    val fileDirectoryPath = item.virtualFile.parent?.path ?: return null

    return fileDirectoryPath == basePath
  }


  private fun matchesFileTypeInQuery(item: PsiFileSystemItem, searchQuery: String): Boolean? {
    val fileExtension = item.virtualFile.extension
    val extensionInQuery = searchQuery.substringAfterLast('.', missingDelimiterValue = "")
    if (extensionInQuery.isEmpty() || fileExtension == null) {
      return null
    }

    return extensionInQuery.lowercase() == fileExtension.lowercase()
  }
}

private fun VirtualFile.toNioPathOrNull(): Path? {
  return try {
    this.toNioPath()
  }
  catch (e: UnsupportedOperationException) {
    null
  }
  catch (e: InvalidPathException) {
    null
  }
}
