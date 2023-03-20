package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_BOOKMARK_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_DIRECTORY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_EXACT_MATCH_DATA_KEY
import com.intellij.ide.bookmarks.BookmarkManager
import com.intellij.psi.PsiFileSystemItem


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
      BookmarkManager.getInstance(project).also {
        it.addFileBookmark(file.virtualFile, "xxx")
      }
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
}
