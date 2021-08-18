package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_USAGE_RATIO_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_DAY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_HOUR_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_MONTH_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_DIRECTORY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_FAVORITE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_SAME_MODULE
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.PRIORITY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.RECENT_INDEX_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.TIME_SINCE_LAST_FILETYPE_USAGE
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.TIME_SINCE_LAST_MODIFICATION_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_DAY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY
import com.intellij.ide.favoritesTreeView.FavoritesManager
import com.intellij.internal.statistic.local.FileTypeUsageSummary
import com.intellij.internal.statistic.local.FileTypeUsageSummaryProvider
import com.intellij.internal.statistic.local.TestFileTypeUsageSummaryProvider
import com.intellij.mock.MockPsiDirectory
import com.intellij.mock.MockPsiFile
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtil
import com.intellij.util.Time
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId


internal class SearchEverywhereFileFeaturesProviderTest
  : HeavyFeaturesProviderTestCase<SearchEverywhereFileFeaturesProvider>(SearchEverywhereFileFeaturesProvider::class.java) {

  private val testFile: PsiFileSystemItem by lazy {
    val file = createTempVirtualFile("testFile.java", null, "", StandardCharsets.UTF_8)
    PsiUtil.getPsiFile(project, file)
  }

  private val mockedFileStatsProvider
    get() = project.service<FileTypeUsageSummaryProvider>() as TestFileTypeUsageSummaryProvider

  private val currentTime: Long
  private val lastMinute: Long
  private val lastHour: Long
  private val lastDay: Long
  private val lastMonth: Long

  init {
    val localDt = LocalDateTime.of(2020, 12, 31, 23, 59)
    localDt.atZone(ZoneId.systemDefault()).let {
      currentTime = it.toInstant().toEpochMilli()
      lastMinute = it.minusMinutes(1).toInstant().toEpochMilli()
      lastHour = it.minusHours(1).toInstant().toEpochMilli()
      lastDay = it.minusDays(1).toInstant().toEpochMilli()
      lastMonth = it.minusWeeks(4).toInstant().toEpochMilli()
    }
  }

  fun testIsDirectory() {
    val directory = MockPsiDirectory(project, project)

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
    val addFileToFavorites = { file: PsiFileSystemItem ->
      FavoritesManager.getInstance(project).also {
        it.createNewList("xxx")
        if (!it.addRoots("xxx", module, file.virtualFile)) {
          fail("Failed to add the file to the favorites list")
        }
      }
    }

    checkThatFeature(IS_FAVORITE_DATA_KEY)
      .ofElement(testFile)
      .changes(false, true)
      .after { addFileToFavorites(it) }
  }

  fun testIsOpened() {
    checkThatFeature(SearchEverywhereFileFeaturesProvider.IS_OPENED_DATA_KEY)
      .ofElement(testFile)
      .changes(false, true)
      .after { FileEditorManager.getInstance(project).openFile(it.virtualFile, false) }
  }

  fun testRecentIndexOfNeverOpenedFile() {
    prepareForRecentIndexTest()

    checkThatFeature(RECENT_INDEX_DATA_KEY)
      .ofElement(testFile)
      .isEqualTo(-1)
  }

  fun testMostRecentFileIndex() {
    val openedFiles = prepareForRecentIndexTest()

    checkThatFeature(RECENT_INDEX_DATA_KEY)
      .ofElement(openedFiles.last()) // Last opened file (i.e. the most recent)
      .isEqualTo(1)
  }

  fun testOldestFileIndex() {
    val openedFiles = prepareForRecentIndexTest()
    val expectedIndex = openedFiles.size

    checkThatFeature(RECENT_INDEX_DATA_KEY)
      .ofElement(openedFiles.first()) // First opened file (i.e. the oldest)
      .isEqualTo(expectedIndex)
  }

  fun testPriority() {
    val priority = 10101

    checkThatFeature(PRIORITY_DATA_KEY)
      .ofElement(testFile)
      .withPriority(priority)
      .isEqualTo(priority)
  }

  fun testFileTypeUsageRatio() {
    mockedFileStatsProvider
      .setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(7, lastDay))
      .setStats("Python", FileTypeUsageSummary(2, lastDay))
      .setStats("XML", FileTypeUsageSummary(1, lastMinute))

    checkThatFeature(FILETYPE_USAGE_RATIO_DATA_KEY)
      .ofElement(testFile)
      .isEqualTo(0.7)
  }

  fun testFileTypeUsedInLastMinute() {
    mockedFileStatsProvider.setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastMinute))

    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to 1.0,
      TIME_SINCE_LAST_FILETYPE_USAGE to currentTime - lastMinute,

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY to true,
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY to true,
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY to true,
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(testFile)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeUsedInLastHour() {
    mockedFileStatsProvider.setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastHour))

    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to 1.0,
      TIME_SINCE_LAST_FILETYPE_USAGE to currentTime - lastHour,

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY to true,
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY to true,
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(testFile)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeUsedInLastDay() {
    mockedFileStatsProvider.setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastDay))

    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to 1.0,
      TIME_SINCE_LAST_FILETYPE_USAGE to currentTime - lastDay,

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY to true,
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(testFile)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeUsedInLastMonth() {
    mockedFileStatsProvider.setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastMonth))

    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to 1.0,
      TIME_SINCE_LAST_FILETYPE_USAGE to currentTime - lastMonth,

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(testFile)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeNeverUsed() {
    mockedFileStatsProvider.clearStats()

    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to 0.0,
      TIME_SINCE_LAST_FILETYPE_USAGE to Long.MAX_VALUE,

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY to false,
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY to false,
    )

    checkThatFeatures()
      .ofElement(testFile)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testModifiedInLastMinute() {
    val file = createFileWithModTimestamp(lastMinute)

    val expectedValues = mapOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to currentTime - lastMinute,

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY to true,
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY to true,
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY to true,
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testModifiedInLastHour() {
    val file = createFileWithModTimestamp(lastHour)

    val expectedValues = mapOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to currentTime - lastHour,

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY to true,
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY to true,
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testModifiedInLastDay() {
    val file = createFileWithModTimestamp(lastDay)

    val expectedValues = mapOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to currentTime - lastDay,

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY to true,
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testModifiedInLastMonth() {
    val file = createFileWithModTimestamp(lastMonth)

    val expectedValues = mapOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to currentTime - lastMonth,

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY to true,
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testModifiedLaterThanMonth() {
    val modTime = lastMonth - Time.DAY
    val file = createFileWithModTimestamp(modTime)

    val expectedValues = mapOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to currentTime - modTime,

      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY to false,
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY to false,
    )

    checkThatFeatures()
      .ofElement(file)
      .withCurrentTime(currentTime)
      .isEqualTo(expectedValues)
  }

  fun testFileInDifferentModule() {
    val (_, moduleAFiles) = createModuleWithJavaFiles("testModuleA", 1)
    val (_, moduleBFiles) = createModuleWithJavaFiles("testModuleB", 1)

    FileEditorManager.getInstance(project).openFile(moduleAFiles.first(), true)

    val psiFile = PsiUtil.getPsiFile(project, moduleBFiles.first())
    checkThatFeature(IS_SAME_MODULE)
      .ofElement(psiFile)
      .isEqualTo(false)
  }

  fun testFileInTheSameModule() {
    val (_, files) = createModuleWithJavaFiles("testModule", 2)

    FileEditorManager.getInstance(project).openFile(files.first(), true)

    val psiFile = PsiUtil.getPsiFile(project, files.last())
    checkThatFeature(IS_SAME_MODULE)
      .ofElement(psiFile)
      .isEqualTo(true)
  }

  private fun createFileWithModTimestamp(modificationTimestamp: Long): PsiFileSystemItem {
    val mockVirtualFile = object : MockVirtualFile("file.java") {
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
      val file = createTempVirtualFile("file$it.java", null, "", StandardCharsets.UTF_8)
      MockPsiFile(file, PsiManager.getInstance(project))
    }.onEach { file -> editor.openFile(file.virtualFile, true) }
  }

  private fun createModuleWithJavaFiles(moduleName: String, numberOfFiles: Int): Pair<Module, List<VirtualFile>> {
    val moduleDir = createTempDir(moduleName)
    val module = createModuleAt(moduleName, project, moduleType, moduleDir.toPath())
    val srcDir = File(moduleDir, "src").apply { mkdir() }
    val srcUrl = VfsUtilCore.pathToUrl(srcDir.toPath().toString())

    val createdFiles = (1..numberOfFiles).map {
      val file = File(srcDir, "file$it.java").apply { createNewFile() }
      VirtualFileManager.getInstance().refreshAndFindFileByNioPath(file.toPath())!!
    }.toList()

    ModuleRootModificationUtil.updateModel(module) { model ->
      val contentEntry = model.addContentEntry(srcUrl)
      contentEntry.addSourceFolder(srcUrl, false)
    }

    return Pair(module, createdFiles)
  }

  private fun closeAllOpenedFiles() {
    val editor = FileEditorManager.getInstance(project)
    editor.openFiles.forEach { editor.closeFile(it) }
  }

  override fun tearDown() {
    EditorHistoryManager.getInstance(project).removeAllFiles()
    mockedFileStatsProvider.clearStats()
    super.tearDown()
  }
}