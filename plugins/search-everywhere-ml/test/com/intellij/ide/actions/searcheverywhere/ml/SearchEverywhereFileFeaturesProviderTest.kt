package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.FILETYPE_USAGE_RATIO_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_DAY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_HOUR_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.FILETYPE_USED_IN_LAST_MONTH_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.IS_SAME_MODULE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.PACKAGE_DISTANCE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.PACKAGE_DISTANCE_NORMALIZED_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereClassOrFileFeaturesProvider.Companion.TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.FILETYPE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_DIRECTORY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.IS_FAVORITE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.PRIORITY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.RECENT_INDEX_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.TIME_SINCE_LAST_MODIFICATION_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_DAY_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFileFeaturesProvider.Companion.WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY
import com.intellij.ide.favoritesTreeView.FavoritesManager
import com.intellij.internal.statistic.local.FileTypeUsageSummary
import com.intellij.internal.statistic.local.FileTypeUsageSummaryProvider
import com.intellij.internal.statistic.local.TestFileTypeUsageSummaryProvider
import com.intellij.mock.MockPsiFile
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.util.Time
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId


internal class SearchEverywhereFileFeaturesProviderTest
  : HeavyFeaturesProviderTestCase<SearchEverywhereFileFeaturesProvider>(SearchEverywhereFileFeaturesProvider::class.java) {

  private val testFile: PsiFileSystemItem by lazy {
    val dir = createTempDir("testFileDir")
    createTextFileInDirectory(dir, "testFile.txt").toPsi()
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

  fun `test file type usage ratio for the most popular file type`() {
    mockedFileStatsProvider
      .setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(7, lastDay))
      .setStats("Python", FileTypeUsageSummary(2, lastDay))
      .setStats("XML", FileTypeUsageSummary(1, lastMinute))

    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to 0.7,
      FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY to 1.0,
      FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY to 7.0,
    )

    checkThatFeatures()
      .ofElement(testFile)
      .isEqualTo(expectedValues)
  }

  fun `test file type usage ratio for the least popular file type`() {
    mockedFileStatsProvider
      .setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastDay))
      .setStats("Python", FileTypeUsageSummary(2, lastDay))
      .setStats("XML", FileTypeUsageSummary(7, lastMinute))

    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to 0.1,
      FILETYPE_USAGE_RATIO_TO_MAX_DATA_KEY to roundDouble(1.0 / 7.0),
      FILETYPE_USAGE_RATIO_TO_MIN_DATA_KEY to 1.0,
    )

    checkThatFeatures()
      .ofElement(testFile)
      .isEqualTo(expectedValues)
  }

  fun testFileTypeUsedInLastMinute() {
    mockedFileStatsProvider.setStats(testFile.virtualFile.fileType.name, FileTypeUsageSummary(1, lastMinute))

    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to 1.0,
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY to currentTime - lastMinute,

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
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY to currentTime - lastHour,

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
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY to currentTime - lastDay,

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
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY to currentTime - lastMonth,

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

    // We expect these features to be null, i.e. not reported
    val expectedValues = mapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to null,
      TIME_SINCE_LAST_FILETYPE_USAGE_DATA_KEY to null,

      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY to null,
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY to null,
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY to null,
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY to null,
    )

    checkThatFeatures()
      .ofElement(testFile)
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
    val moduleAFiles = createModuleWithTextFiles("testModuleA", 1)
    val moduleBFiles = createModuleWithTextFiles("testModuleB", 1)

    FileEditorManager.getInstance(project).openFile(moduleAFiles.first(), true)

    val psiFile = moduleBFiles.first().toPsi()
    checkThatFeature(IS_SAME_MODULE_DATA_KEY)
      .ofElement(psiFile)
      .isEqualTo(false)
  }

  fun testFileInTheSameModule() {
    val files = createModuleWithTextFiles("testModule", 2)

    FileEditorManager.getInstance(project).openFile(files.first(), true)

    val psiFile = files.last().toPsi()
    checkThatFeature(IS_SAME_MODULE_DATA_KEY)
      .ofElement(psiFile)
      .isEqualTo(true)
  }

  fun `test if same module is reported for directories`() {
    val moduleFiles = createModuleWithTextFiles("testModuleA", 1)
    val foundDirectory = File(moduleFiles.first().parent.path, "foundDir").apply { mkdir() }

    FileEditorManager.getInstance(project).openFile(moduleFiles.first(), true)

    val psiFile = foundDirectory.toPsi()
    checkThatFeature(IS_SAME_MODULE_DATA_KEY)
      .ofElement(psiFile)
      .isEqualTo(true)

  }

  fun `test package distance is reported for directories`() {
    val srcDir = createModuleWithSrcDir("packageTestModule")
    val packageDir = createPackageDirectory(srcDir, "a.b.c")
    val openedFile = createTextFileInDirectory(packageDir, "fileB.txt")
    val foundDirectory = createDirectoryInDirectory(packageDir, "foundDirectory")

    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 1,  // The found directory is considered a subpackage, hence the distance should be 1
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to roundDouble(1 / (3 + 4).toDouble()),
    )

    val psiFile = foundDirectory.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance is 0 when same package`() {
    val srcDir = createModuleWithSrcDir("packageTestModule")
    val packageDir = createPackageDirectory(srcDir, "a.b.c.d")
    val openedFile = createTextFileInDirectory(packageDir, "fileA.txt")
    val foundFile = createTextFileInDirectory(packageDir, "fileB.txt")

    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 0,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to 0.0,
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance is 1 when in child package`() {
    val srcDir = createModuleWithSrcDir("packageTestModule")
    val packageDir = createPackageDirectory(srcDir, "a.b.c.d")
    val childPackageDir = createPackageDirectory(srcDir, "a.b.c.d.e")
    val openedFile = createTextFileInDirectory(packageDir, "fileA.txt")
    val foundFile = createTextFileInDirectory(childPackageDir, "fileB.txt")

    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 1,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to roundDouble(1 / (4 + 5).toDouble()),
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance is 1 when in parent package`() {
    val srcDir = createModuleWithSrcDir("packageTestModule")
    val packageDir = createPackageDirectory(srcDir, "a.b.c.d")
    val parentPackageDir = createPackageDirectory(srcDir, "a.b.c")
    val openedFile = createTextFileInDirectory(packageDir, "fileA.txt")
    val foundFile = createTextFileInDirectory(parentPackageDir, "fileB.txt")

    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 1,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to roundDouble(1 / (4 + 3).toDouble()),
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance on a parent of different group`() {
    val srcDir = createModuleWithSrcDir("packageTestModule")
    val packageDir = createPackageDirectory(srcDir, "a.b.c.d")
    val otherPackageDir = createPackageDirectory(srcDir, "a.b.x")
    val openedFile = createTextFileInDirectory(packageDir, "fileA.txt")
    val foundFile = createTextFileInDirectory(otherPackageDir, "fileB.txt")

    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 3,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to roundDouble(3 / (4 + 3).toDouble()),
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance on a child of different group`() {
    val srcDir = createModuleWithSrcDir("packageTestModule")
    val packageDir = createPackageDirectory(srcDir, "a.b.c.d")
    val otherPackageDir = createPackageDirectory(srcDir, "a.b.x.y")
    val openedFile = createTextFileInDirectory(packageDir, "fileA.txt")
    val foundFile = createTextFileInDirectory(otherPackageDir, "fileB.txt")

    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 4,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to roundDouble(4 / (4 + 4).toDouble())
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance when root is different`() {
    val srcDir = createModuleWithSrcDir("packageTestModule")
    val packageDir = createPackageDirectory(srcDir, "a.b.c.d")
    val otherPackageDir = createPackageDirectory(srcDir, "x.y")
    val openedFile = createTextFileInDirectory(packageDir, "fileA.txt")
    val foundFile = createTextFileInDirectory(otherPackageDir, "fileB.txt")

    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 6,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to roundDouble(6 / (4 + 2).toDouble()),
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance is 0 when files are not in packages`() {
    val srcDir = createModuleWithSrcDir("packageTestModule")
    val openedFile = createTextFileInDirectory(srcDir, "fileA.txt")
    val foundFile = createTextFileInDirectory(srcDir, "fileB.txt")

    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 0,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to 0.0,
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
  }

  fun `test package distance when one file is not in a package`() {
    val srcDir = createModuleWithSrcDir("packageTestModule")
    val packageDir = createPackageDirectory(srcDir, "a.b.c.d")
    val openedFile = createTextFileInDirectory(packageDir, "fileA.txt")
    val foundFile = createTextFileInDirectory(srcDir, "fileB.txt")

    FileEditorManager.getInstance(project).openFile(openedFile, true)

    val expected = mapOf(
      PACKAGE_DISTANCE_DATA_KEY to 4,
      PACKAGE_DISTANCE_NORMALIZED_DATA_KEY to roundDouble(4 / (4 + 0).toDouble()),
    )

    val psiFile = foundFile.toPsi()
    checkThatFeatures()
      .ofElement(psiFile)
      .isEqualTo(expected)
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

  /**
   * @return List of created files
   */
  private fun createModuleWithTextFiles(moduleName: String, numberOfFiles: Int): List<VirtualFile> {
    val srcDir = createModuleWithSrcDir(moduleName)

    val createdFiles = (1..numberOfFiles).map {
      File(srcDir, "file$it.txt").apply { createNewFile() }.toVirtualFile()
    }.toList()

    return createdFiles
  }

  private fun createPackageDirectory(srcDirectory: File, packageStatement: String): File {
    var dir = srcDirectory
    packageStatement.split('.')
      .forEach { dir = File(dir, it).apply { mkdir() } }

    return dir
  }

  /**
   * @return Source directory
   */
  private fun createModuleWithSrcDir(moduleName: String): File {
    val moduleDir = createTempDir(moduleName)
    val module = createModuleAt(moduleName, project, moduleType, moduleDir.toPath())
    val srcDir = File(moduleDir, "src").apply { mkdir() }

    ModuleRootModificationUtil.updateModel(module) { model ->
      val srcDirUrl = VfsUtilCore.pathToUrl(srcDir.path)
      val contentEntry = model.addContentEntry(srcDirUrl)
      contentEntry.addSourceFolder(srcDirUrl, false)
    }

    return srcDir
  }

  private fun createTextFileInDirectory(parentDir: File, filename: String): VirtualFile {
    val vfManager = VirtualFileManager.getInstance()

    val file = PsiFileFactory.getInstance(project).createFileFromText(filename, PlainTextFileType.INSTANCE, "")
    val psiDirectory = parentDir.toPsi()
    WriteAction.computeAndWait<Unit, Nothing> {
      psiDirectory.add(file)
    }

    return vfManager.refreshAndFindFileByUrl(psiDirectory.virtualFile.url + "/${file.name}")!!
  }

  private fun createDirectoryInDirectory(parentDir: File, name: String): VirtualFile {
    return File(parentDir, name).apply {
      mkdir()
    }.toVirtualFile()
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
