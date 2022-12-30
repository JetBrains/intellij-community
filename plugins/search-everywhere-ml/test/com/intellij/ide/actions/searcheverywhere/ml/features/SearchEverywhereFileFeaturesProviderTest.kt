package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_BOOKMARK_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_DIRECTORY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_EXACT_MATCH_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.RECENT_INDEX_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.TIME_SINCE_LAST_MODIFICATION_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_DAY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY
import com.intellij.ide.bookmarks.BookmarkManager
import com.intellij.mock.MockPsiFile
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.util.Time
import java.nio.charset.StandardCharsets


internal class SearchEverywhereFileFeaturesProviderTest
  : SearchEverywhereBaseFileFeaturesProviderTest<SearchEverywhereFileFeaturesProvider>(SearchEverywhereFileFeaturesProvider::class.java) {

  fun testIsDirectory() {
    val directory = createTempDirectory().toPsi()

    checkThatFeature(IS_DIRECTORY_DATA_KEY.name)
      .ofElement(directory)
      .isEqualTo(true)
  }

  fun testFileIsNotDirectory() {
    checkThatFeature(IS_DIRECTORY_DATA_KEY.name)
      .ofElement(testFile)
      .isEqualTo(false)
  }

  fun testFileType() {
    checkThatFeature(FILETYPE_DATA_KEY.name)
      .ofElement(testFile)
      .isEqualTo(testFile.virtualFile.fileType.name)
  }

  fun testIsInFavorites() {
    val addFileToBookmarks = { file: PsiFileSystemItem ->
      BookmarkManager.getInstance(project).also {
        it.addFileBookmark(file.virtualFile, "xxx")
      }
    }

    checkThatFeature(IS_BOOKMARK_DATA_KEY.name)
      .ofElement(testFile)
      .changes(false, true)
      .after { addFileToBookmarks(it) }
  }

  fun testIsOpened() {
    checkThatFeature(SearchEverywhereFileFeaturesProvider.IS_OPENED_DATA_KEY.name)
      .ofElement(testFile)
      .changes(false, true)
      .after { FileEditorManager.getInstance(project).openFile(it.virtualFile, false) }
  }

  fun testRecentIndexOfNeverOpenedFile() {
    prepareForRecentIndexTest()

    checkThatFeature(RECENT_INDEX_DATA_KEY.name)
      .ofElement(testFile)
      .isEqualTo(-1)
  }

  fun testMostRecentFileIndex() {
    val openedFiles = prepareForRecentIndexTest()

    checkThatFeature(RECENT_INDEX_DATA_KEY.name)
      .ofElement(openedFiles.last()) // Last opened file (i.e. the most recent)
      .isEqualTo(1)
  }

  fun testOldestFileIndex() {
    val openedFiles = prepareForRecentIndexTest()
    val expectedIndex = openedFiles.size

    checkThatFeature(RECENT_INDEX_DATA_KEY.name)
      .ofElement(openedFiles.first()) // First opened file (i.e. the oldest)
      .isEqualTo(expectedIndex)
  }

  fun testModifiedInLastMinute() {
    val file = createFileWithModTimestamp(lastMinute)

    val expectedValues = listOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY.with(currentTime - lastMinute),

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY.with(true),
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY.with(true),
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY.with(true),
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY.with(true),
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testModifiedInLastHour() {
    val file = createFileWithModTimestamp(lastHour)

    val expectedValues = listOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY.with(currentTime - lastHour),

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY.with(true),
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY.with(true),
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY.with(true),
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testModifiedInLastDay() {
    val file = createFileWithModTimestamp(lastDay)

    val expectedValues = listOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY.with(currentTime - lastDay),

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY.with(true),
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY.with(true),
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testModifiedInLastMonth() {
    val file = createFileWithModTimestamp(lastMonth)

    val expectedValues = listOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY.with(currentTime - lastMonth),

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY.with(true),
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testModifiedLaterThanMonth() {
    val modTime = lastMonth - Time.DAY
    val file = createFileWithModTimestamp(modTime)

    val expectedValues = listOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY.with(currentTime - modTime),

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY.with(false),
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY.with(false),
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun `test exact match is true when priority is exactly exact match degree`() {
    checkThatFeature(IS_EXACT_MATCH_DATA_KEY.name)
      .ofElement(testFile)
      .withPriority(GotoFileItemProvider.EXACT_MATCH_DEGREE)
      .isEqualTo(true)
  }

  fun `test exact match is false when only slash is first character of query`() {
    checkThatFeature(IS_EXACT_MATCH_DATA_KEY.name)
      .ofElement(testFile)
      .withPriority(GotoFileItemProvider.EXACT_MATCH_DEGREE + 1)
      .withQuery("/${testFile.virtualFile.name}")
      .isEqualTo(false)
  }

  fun `test exact match is true when query starts with slash`() {
    checkThatFeature(IS_EXACT_MATCH_DATA_KEY.name)
      .ofElement(testFile)
      .withPriority(GotoFileItemProvider.EXACT_MATCH_DEGREE + 1)
      .withQuery("/${testFile.virtualFile.parent.name}/${testFile.virtualFile.name}")
      .isEqualTo(true)
  }

  fun `test exact match is true when last slash is not first character of query`() {
    checkThatFeature(IS_EXACT_MATCH_DATA_KEY.name)
      .ofElement(testFile)
      .withPriority(GotoFileItemProvider.EXACT_MATCH_DEGREE + 1)
      .withQuery("${testFile.virtualFile.parent.name.last()}/${testFile.virtualFile.name}")
      .isEqualTo(true)
  }

  fun `test exact match is true when using backslash`() {
    checkThatFeature(IS_EXACT_MATCH_DATA_KEY.name)
      .ofElement(testFile)
      .withPriority(GotoFileItemProvider.EXACT_MATCH_DEGREE + 1)
      .withQuery("${testFile.virtualFile.parent.name.last()}\\${testFile.virtualFile.name}")
      .isEqualTo(true)
  }

  private fun createFileWithModTimestamp(modificationTimestamp: Long): PsiFileSystemItem {
    val mockVirtualFile = object : MockVirtualFile("file.txt") {
      override fun getTimeStamp(): Long {
        return modificationTimestamp
      }
    }

    return MockPsiFile(mockVirtualFile, psiManager)
  }

  private fun prepareForRecentIndexTest(numberOfFiles: Int = 3): List<PsiFileSystemItem> {
    closeAllOpenedFiles()
    EditorHistoryManager.getInstance(project).removeAllFiles()

    val editor = FileEditorManager.getInstance(project)
    return (1..numberOfFiles).map {
      val file = createTempVirtualFile("file$it.txt", null, "", StandardCharsets.UTF_8)
      MockPsiFile(file, PsiManager.getInstance(project))
    }.onEach { file -> editor.openFile(file.virtualFile, true) }
  }

  private fun closeAllOpenedFiles() {
    val editor = FileEditorManager.getInstance(project)
    editor.openFiles.forEach { editor.closeFile(it) }
  }
}
