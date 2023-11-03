package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.providers.LineBookmarkProvider
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.psi.PsiFileSystemItem
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereFileFeaturesProvider.Fields.FILETYPE_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereFileFeaturesProvider.Fields.IS_BOOKMARK_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereFileFeaturesProvider.Fields.IS_DIRECTORY_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereFileFeaturesProvider.Fields.IS_EXACT_MATCH_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereFileFeaturesProvider.Fields.IS_EXACT_MATCH_WITH_REL_PATH_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereFileFeaturesProvider.Fields.REL_PATH_NAME_FEATURE_TO_FIELD


internal class SearchEverywhereFileFeaturesProviderTest
  : SearchEverywhereBaseFileFeaturesProviderTest<SearchEverywhereFileFeaturesProvider>(SearchEverywhereFileFeaturesProvider::class.java) {

  fun testIsDirectory() {
    val directory = createTempDirectory().toPsi()

    checkThatFeature(IS_DIRECTORY_DATA_KEY)
      .ofElement(directory)
      .isEqualTo(true)
  }

  fun testFileIsNotDirectory() {
    checkThatFeature(IS_DIRECTORY_DATA_KEY)
      .ofElement(testFile)
      .isEqualTo(false)
  }

  fun testFileType() {
    checkThatFeature(FILETYPE_DATA_KEY)
      .ofElement(testFile)
      .isEqualTo(testFile.virtualFile.fileType.name)
  }

  fun testIsInFavorites() {
    val addFileToBookmarks = { file: PsiFileSystemItem ->
      val manager = BookmarksManager.getInstance(project)
      val bookmark = LineBookmarkProvider.find(project)?.createBookmark(file.virtualFile)
      if (manager != null && bookmark != null)
        manager.add(bookmark, com.intellij.ide.bookmark.BookmarkType.DEFAULT)
    }

    checkThatFeature(IS_BOOKMARK_DATA_KEY)
      .ofElement(testFile)
      .changes(false, true)
      .after { addFileToBookmarks(it) }
  }


  fun `test exact match is true when priority is exactly exact match degree`() {
    checkThatFeature(IS_EXACT_MATCH_DATA_KEY)
      .ofElement(testFile)
      .withPriority(GotoFileItemProvider.EXACT_MATCH_DEGREE)
      .isEqualTo(true)
  }

  fun `test exact match is false when only slash is first character of query`() {
    checkThatFeature(IS_EXACT_MATCH_DATA_KEY)
      .ofElement(testFile)
      .withPriority(GotoFileItemProvider.EXACT_MATCH_DEGREE + 1)
      .withQuery("/${testFile.virtualFile.name}")
      .isEqualTo(false)
  }

  fun `test exact match is true when query starts with slash`() {
    checkThatFeature(IS_EXACT_MATCH_DATA_KEY)
      .ofElement(testFile)
      .withPriority(GotoFileItemProvider.EXACT_MATCH_DEGREE + 1)
      .withQuery("/${testFile.virtualFile.parent.name}/${testFile.virtualFile.name}")
      .isEqualTo(true)
  }

  fun `test exact match is true when last slash is not first character of query`() {
    checkThatFeature(IS_EXACT_MATCH_DATA_KEY)
      .ofElement(testFile)
      .withPriority(GotoFileItemProvider.EXACT_MATCH_DEGREE + 1)
      .withQuery("${testFile.virtualFile.parent.name.last()}/${testFile.virtualFile.name}")
      .isEqualTo(true)
  }

  fun `test exact match is true when using backslash`() {
    checkThatFeature(IS_EXACT_MATCH_DATA_KEY)
      .ofElement(testFile)
      .withPriority(GotoFileItemProvider.EXACT_MATCH_DEGREE + 1)
      .withQuery("${testFile.virtualFile.parent.name.last()}\\${testFile.virtualFile.name}")
      .isEqualTo(true)
  }

  fun `test exact relative path is true with slash`() {
    checkThatFeature(IS_EXACT_MATCH_WITH_REL_PATH_DATA_KEY)
      .ofElement(testFile)
      .withQuery("${testFile.virtualFile.parent.name}/${testFile.virtualFile.name}")
      .isEqualTo(true)
  }

  fun `test exact relative path is true with back slash`() {
    checkThatFeature(IS_EXACT_MATCH_WITH_REL_PATH_DATA_KEY)
      .ofElement(testFile)
      .withQuery("${testFile.virtualFile.parent.name}\\${testFile.virtualFile.name}")
      .isEqualTo(true)
  }

  fun `test exact relative path is false for just file`() {
    checkThatFeature(IS_EXACT_MATCH_WITH_REL_PATH_DATA_KEY)
      .ofElement(testFile)
      .withQuery(testFile.virtualFile.name)
      .isEqualTo(false)
  }

  fun `test relPathPrefixMatchedWordsRelative`() {
    val relativePath = "${testFile.virtualFile.parent.name}\\${testFile.virtualFile.name}"
    checkThatFeature(REL_PATH_NAME_FEATURE_TO_FIELD["prefix_matched_words_relative"]!! as EventField<Double>)
      .ofElement(testFile)
      .withQuery(relativePath)
      .isEqualTo(1.0)
  }
}
